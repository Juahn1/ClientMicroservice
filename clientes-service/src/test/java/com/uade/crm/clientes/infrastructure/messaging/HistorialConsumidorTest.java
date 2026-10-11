package com.uade.crm.clientes.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uade.crm.clientes.CuilDePrueba;
import com.uade.crm.clientes.MensajesKafka;
import com.uade.crm.clientes.PruebaIntegracion;
import com.uade.crm.clientes.application.AnonimizacionClienteService;
import com.uade.crm.clientes.application.RegistroCalificacionService;
import com.uade.crm.clientes.config.ClientesProperties;
import com.uade.crm.clientes.domain.ClienteFactory;
import com.uade.crm.clientes.domain.model.CalificacionHistorica;
import com.uade.crm.clientes.domain.port.ClienteRepositorio;
import com.uade.crm.clientes.domain.port.HistorialCalificaciones;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * El consumidor de {@code lead-calificado} contra Kafka y PostgreSQL reales (capability
 * clientes-historial-calificaciones, design D10 del change 08).
 *
 * <p>Los mensajes de un mismo {@code leadId} van a la misma partición y se procesan en orden. Para comprobar que un
 * mensaje no se registró, los tests publican después otro con la misma clave y esperan a que se registre ese.
 */
@PruebaIntegracion
@Import(MensajesKafka.class)
@ExtendWith(OutputCaptureExtension.class)
class HistorialConsumidorTest {

    private static final Duration ESPERA = Duration.ofSeconds(20);

    private final ClienteFactory factory = CuilDePrueba.factory(Clock.systemUTC());

    @Autowired
    MensajesKafka mensajes;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ClienteRepositorio clientes;

    @Autowired
    HistorialCalificaciones historial;

    @Autowired
    AnonimizacionClienteService anonimizacion;

    @Autowired
    RegistroCalificacionService registro;

    @Autowired
    TransactionTemplate transaccion;

    @Autowired
    ClientesProperties properties;

    private String topico;
    private String topicoFallidos;

    @BeforeEach
    void preparar() {
        limpiar();
        topico = properties.kafka().topicos().leadCalificado();
        topicoFallidos = properties.kafka().topicos().leadCalificadoDlt();
    }

    /** También corre antes de cada test (desde preparar): las demás clases del contexto compartido dejan sus datos. */
    @AfterEach
    void limpiar() {
        jdbc.execute("DROP TRIGGER IF EXISTS prueba_falla ON historial_calificacion");
        jdbc.execute("DROP FUNCTION IF EXISTS prueba_falla()");
        jdbc.execute("DROP SEQUENCE IF EXISTS prueba_fallas");
        jdbc.update("DELETE FROM historial_calificacion");
        jdbc.update("DELETE FROM consentimiento");
        jdbc.update("DELETE FROM cliente");
    }

    // Requirement: Registro de las calificaciones publicadas

    @Test
    void calificacionAutomatica() {
        UUID cliente = cliente("martina@gmail.com");
        UUID lead = UUID.randomUUID();

        publicar(lead, evento(lead, cliente, "AUTOMATICA", "2026-10-11T15:30:00", 60, "MUY_POTABLE",
                "Calificación automática calculada por motor de reglas",
                List.of("[REGLA_COBERTURA]: +60", "[REGLA_FINANCIACION]: 0", "[REGLA_UBICACION]: 0")));

        CalificacionHistorica registrada = esperarCalificaciones(cliente, 1).get(0);
        assertThat(registrada.leadId()).isEqualTo(lead);
        assertThat(registrada.puntaje()).isEqualTo(60);
        assertThat(registrada.nivel()).isEqualTo("MUY_POTABLE");
        assertThat(registrada.origen()).isEqualTo("AUTOMATICA");
        assertThat(registrada.motivos())
                .containsExactly("[REGLA_COBERTURA]: +60", "[REGLA_FINANCIACION]: 0", "[REGLA_UBICACION]: 0");
    }

    @Test
    void correccionManual() {
        UUID cliente = cliente("martina@gmail.com");
        UUID lead = UUID.randomUUID();

        publicar(lead, evento(lead, cliente, "AUTOMATICA", "2026-10-11T15:30:00", 60, "MUY_POTABLE", null, List.of()));
        publicar(lead, evento(lead, cliente, "MANUAL", "2026-10-11T16:00:00", 35, "POTABLE", "Anticipo confirmado",
                List.of()));

        List<CalificacionHistorica> calificaciones = esperarCalificaciones(cliente, 2);
        assertThat(calificaciones).extracting(CalificacionHistorica::leadId).containsOnly(lead);
        assertThat(calificaciones.get(0).origen()).isEqualTo("MANUAL");
        assertThat(calificaciones.get(0).observacion()).isEqualTo("Anticipo confirmado");
    }

    @Test
    void puntajeNegativoYNivelDesconocido() {
        UUID cliente = cliente("martina@gmail.com");
        UUID lead = UUID.randomUUID();

        publicar(lead, evento(lead, cliente, "AUTOMATICA", "2026-10-11T15:30:00", -25, "RECONTRA_POTABLE", null,
                List.of()));

        CalificacionHistorica registrada = esperarCalificaciones(cliente, 1).get(0);
        assertThat(registrada.puntaje()).isEqualTo(-25);
        assertThat(registrada.nivel()).isEqualTo("RECONTRA_POTABLE");
    }

    @Test
    void sinMotivosNiObservacion() {
        UUID cliente = cliente("martina@gmail.com");
        UUID lead = UUID.randomUUID();
        Map<String, Object> evento = evento(lead, cliente, "AUTOMATICA", "2026-10-11T15:30:00", 20, "POTABLE", null,
                null);
        evento.remove("observacion");
        evento.remove("motivos");

        publicar(lead, evento);

        CalificacionHistorica registrada = esperarCalificaciones(cliente, 1).get(0);
        assertThat(registrada.motivos()).isEmpty();
        assertThat(registrada.observacion()).isNull();
    }

    // Requirement: Formato del evento de calificación

    @Test
    void eventoConSobreComun() {
        UUID cliente = cliente("martina@gmail.com");
        UUID lead = UUID.randomUUID();
        Map<String, Object> sobre = new LinkedHashMap<>();
        sobre.put("eventId", UUID.randomUUID().toString());
        sobre.put("eventType", "LeadCalificado");
        sobre.put("version", 1);
        sobre.put("occurredAt", "2026-10-11T18:30:01Z");
        sobre.put("correlationId", "abc-123");
        sobre.put("payload", evento(lead, cliente, "AUTOMATICA", "2026-10-11T15:30:00", 60, "MUY_POTABLE", null,
                List.of()));

        publicar(lead, sobre);

        assertThat(esperarCalificaciones(cliente, 1).get(0).leadId()).isEqualTo(lead);
    }

    @Test
    void camposAdicionales() {
        UUID cliente = cliente("martina@gmail.com");
        UUID lead = UUID.randomUUID();
        Map<String, Object> evento = evento(lead, cliente, "AUTOMATICA", "2026-10-11T15:30:00", 60, "MUY_POTABLE",
                null, List.of());
        evento.put("sucursal", "Lanús");

        publicar(lead, evento);

        esperarCalificaciones(cliente, 1);
    }

    // Requirement: Fecha de cálculo

    @Test
    void fechaSinZonaYConOffset() {
        UUID cliente = cliente("martina@gmail.com");
        UUID sinZona = UUID.randomUUID();
        UUID conZona = UUID.randomUUID();

        publicar(sinZona, evento(sinZona, cliente, "AUTOMATICA", "2026-10-11T15:30:00", 60, "MUY_POTABLE", null,
                List.of()));
        publicar(conZona, evento(conZona, cliente, "AUTOMATICA", "2026-10-11T15:30:00Z", 60, "MUY_POTABLE", null,
                List.of()));

        assertThat(esperarCalificaciones(cliente, 2))
                .extracting(CalificacionHistorica::leadId, CalificacionHistorica::calculadaEn)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(sinZona, Instant.parse("2026-10-11T18:30:00Z")),
                        org.assertj.core.groups.Tuple.tuple(conZona, Instant.parse("2026-10-11T15:30:00Z")));
    }

    // Requirement: Registro idempotente

    @Test
    void eventoRepetido() {
        UUID cliente = cliente("martina@gmail.com");
        UUID lead = UUID.randomUUID();
        Map<String, Object> automatica = evento(lead, cliente, "AUTOMATICA", "2026-10-11T15:30:00", 60,
                "MUY_POTABLE", null, List.of());

        publicar(lead, automatica);
        publicar(lead, automatica);
        publicar(lead, evento(lead, cliente, "MANUAL", "2026-10-11T16:00:00", 35, "POTABLE", null, List.of()));

        assertThat(esperarCalificaciones(cliente, 2)).extracting(CalificacionHistorica::origen)
                .containsExactly("MANUAL", "AUTOMATICA");
    }

    @Test
    void recalculoDelMismoLead() {
        UUID cliente = cliente("martina@gmail.com");
        UUID lead = UUID.randomUUID();

        publicar(lead, evento(lead, cliente, "AUTOMATICA", "2026-10-11T15:30:00", 60, "MUY_POTABLE", null, List.of()));
        publicar(lead, evento(lead, cliente, "AUTOMATICA", "2026-10-12T09:00:00", 20, "POTABLE", null, List.of()));

        esperarCalificaciones(cliente, 2);
    }

    // Requirement: Solo se registran calificaciones de clientes activos

    @Test
    void clienteAnonimizado(CapturedOutput salida) {
        UUID anonimizado = cliente("martina@gmail.com");
        anonimizacion.anonimizar(anonimizado, "martina@gmail.com");
        UUID activo = cliente("diego@gmail.com");
        UUID lead = UUID.randomUUID();

        publicar(lead, evento(lead, anonimizado, "AUTOMATICA", "2026-10-11T15:30:00", 60, "MUY_POTABLE", null,
                List.of()));
        publicar(lead, evento(lead, activo, "MANUAL", "2026-10-11T16:00:00", 35, "POTABLE", null, List.of()));

        esperarCalificaciones(activo, 1);
        assertThat(filas(anonimizado)).isZero();
        assertThat(salida.getOut()).contains("el cliente " + anonimizado + " está anonimizado");
    }

    @Test
    void clienteInexistente(CapturedOutput salida) {
        UUID inexistente = UUID.randomUUID();
        UUID activo = cliente("diego@gmail.com");
        UUID lead = UUID.randomUUID();

        publicar(lead, evento(lead, inexistente, "AUTOMATICA", "2026-10-11T15:30:00", 60, "MUY_POTABLE", null,
                List.of()));
        publicar(lead, evento(lead, activo, "MANUAL", "2026-10-11T16:00:00", 35, "POTABLE", null, List.of()));

        esperarCalificaciones(activo, 1);
        assertThat(filas(inexistente)).isZero();
        assertThat(salida.getOut()).containsPattern(
                "WARN .*el cliente " + inexistente + " no existe \\(lead " + lead + "\\)");
    }

    /**
     * El registro llega mientras otra transacción anonimiza al cliente: espera la fila del cliente ({@code FOR SHARE}
     * contra {@code FOR UPDATE}), ve el cliente anonimizado y no inserta.
     */
    @Test
    void anonimizacionSimultanea() throws Exception {
        UUID cliente = cliente("martina@gmail.com");
        CountDownLatch filaTomada = new CountDownLatch(1);
        CompletableFuture<Void> baja = CompletableFuture.runAsync(() -> transaccion.executeWithoutResult(estado -> {
            anonimizacion.anonimizar(cliente, "martina@gmail.com");
            filaTomada.countDown();
            dormir(Duration.ofMillis(700));
        }));
        assertThat(filaTomada.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<RegistroCalificacionService.Resultado> alta = CompletableFuture.supplyAsync(
                () -> registro.registrar(new CalificacionHistorica(UUID.randomUUID(), cliente, "MUY_POTABLE", 60,
                        "AUTOMATICA", Instant.parse("2026-10-11T18:30:00Z"), null, List.of())));
        dormir(Duration.ofMillis(300));
        assertThat(alta).as("el registro espera a la anonimización").isNotDone();

        baja.get(10, TimeUnit.SECONDS);
        assertThat(alta.get(10, TimeUnit.SECONDS)).isEqualTo(RegistroCalificacionService.Resultado.CLIENTE_ANONIMIZADO);
        assertThat(filas(cliente)).isZero();
    }

    /**
     * Orden inverso: el registro tiene tomada la fila del cliente ({@code FOR SHARE}) cuando empieza la anonimización,
     * que espera a que confirme y su listener borra la calificación recién registrada.
     */
    @Test
    void anonimizacionMientrasSeRegistra() throws Exception {
        UUID cliente = cliente("martina@gmail.com");
        CountDownLatch registrada = new CountDownLatch(1);
        CompletableFuture<Void> alta = CompletableFuture.runAsync(() -> transaccion.executeWithoutResult(estado -> {
            registro.registrar(new CalificacionHistorica(UUID.randomUUID(), cliente, "MUY_POTABLE", 60, "AUTOMATICA",
                    Instant.parse("2026-10-11T18:30:00Z"), null, List.of()));
            registrada.countDown();
            dormir(Duration.ofMillis(700));
        }));
        assertThat(registrada.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> baja = CompletableFuture.runAsync(
                () -> anonimizacion.anonimizar(cliente, "martina@gmail.com"));
        dormir(Duration.ofMillis(300));
        assertThat(baja).as("la anonimización espera al registro").isNotDone();

        alta.get(10, TimeUnit.SECONDS);
        baja.get(10, TimeUnit.SECONDS);
        assertThat(filas(cliente)).isZero();
    }

    // Requirement: Mensajes que no se pueden procesar

    @Test
    void mensajeIlegible(CapturedOutput salida) {
        UUID cliente = cliente("martina@gmail.com");
        UUID lead = UUID.randomUUID();
        String ilegible = "esto no es JSON " + lead;

        mensajes.publicar(topico, lead, ilegible);
        publicar(lead, evento(lead, cliente, "AUTOMATICA", "2026-10-11T15:30:00", 60, "MUY_POTABLE", null, List.of()));

        esperarCalificaciones(cliente, 1);
        List<ConsumerRecord<String, String>> fallidos = mensajes.esperar(topicoFallidos, lead, 1);
        assertThat(fallidos).singleElement().satisfies(m -> assertThat(m.value()).isEqualTo(ilegible));
        assertThat(salida.getOut()).containsPattern("WARN .*Mensaje de " + topico + " enviado a " + topicoFallidos
                + ": .*clave " + lead + ", causa: .*'mensaje' no es JSON")
                .doesNotContain("esto no es JSON");
    }

    @Test
    void faltaUnCampoObligatorio() {
        UUID cliente = cliente("martina@gmail.com");
        UUID lead = UUID.randomUUID();
        Map<String, Object> sinCliente = evento(lead, cliente, "AUTOMATICA", "2026-10-11T15:30:00", 60, "MUY_POTABLE",
                null, List.of());
        sinCliente.remove("clienteId");

        publicar(lead, sinCliente);

        assertThat(mensajes.esperar(topicoFallidos, lead, 1)).hasSize(1);
        assertThat(filas(cliente)).isZero();
    }

    @Test
    void fallaTransitoriaQueSeRecupera() {
        UUID cliente = cliente("martina@gmail.com");
        UUID lead = UUID.randomUUID();
        fallarAlRegistrar(lead, 1);

        publicar(lead, evento(lead, cliente, "AUTOMATICA", "2026-10-11T15:30:00", 60, "MUY_POTABLE", null, List.of()));

        esperarCalificaciones(cliente, 1);
        assertThat(intentos()).as("un intento fallido y uno exitoso").isEqualTo(2);
        assertThat(mensajes.ninguno(topicoFallidos, lead)).isEmpty();
    }

    @Test
    void fallaPersistente(CapturedOutput salida) {
        UUID cliente = cliente("martina@gmail.com");
        UUID lead = UUID.randomUUID();
        fallarAlRegistrar(lead, 1_000);
        UUID otroLead = UUID.randomUUID();

        publicar(lead, evento(lead, cliente, "AUTOMATICA", "2026-10-11T15:30:00", 60, "MUY_POTABLE", null, List.of()));
        // Misma clave: misma partición, así que se procesa después del que falla.
        mensajes.publicar(topico, lead, escribir(evento(otroLead, cliente, "AUTOMATICA", "2026-10-11T16:00:00", 20,
                "POTABLE", null, List.of())));

        assertThat(mensajes.esperar(topicoFallidos, lead, 1)).singleElement()
                .satisfies(m -> assertThat(m.value()).contains("\"leadId\":\"" + lead + "\""));
        assertThat(esperarCalificaciones(cliente, 1).get(0).leadId()).isEqualTo(otroLead);
        // El intento original más los 3 reintentos del perfil de test.
        assertThat(intentos()).isEqualTo(4);
        assertThat(salida.getOut()).containsPattern("WARN .*enviado a " + topicoFallidos + ": .*clave " + lead
                + ", causa: [A-Za-z]+Exception");
    }

    // Requirement: Datos financieros fuera de los logs

    @Test
    void logsDelRegistroSinMotivosNiObservacion(CapturedOutput salida) {
        LoggingSystem logging = LoggingSystem.get(getClass().getClassLoader());
        try {
            logging.setLogLevel("com.uade.crm", LogLevel.DEBUG);
            logging.setLogLevel("org.springframework.kafka", LogLevel.DEBUG);
            UUID cliente = cliente("martina@gmail.com");
            UUID lead = UUID.randomUUID();
            Map<String, Object> evento = evento(lead, cliente, "AUTOMATICA", "2026-10-11T15:30:00", 60, "MUY_POTABLE",
                    "Anticipo confirmado", List.of("[REGLA_COBERTURA]: Cobertura aportada: 60.00%"));

            publicar(lead, evento);
            publicar(lead, evento);
            publicar(lead, evento(lead, cliente, "MANUAL", "2026-10-11T16:00:00", 35, "POTABLE", null, List.of()));
            esperarCalificaciones(cliente, 2);

            assertThat(salida.getOut()).contains("Calificación registrada: lead " + lead)
                    .contains("ya estaba registrada (lead " + lead)
                    .doesNotContain("Anticipo confirmado")
                    .doesNotContain("Cobertura aportada");
        } finally {
            logging.setLogLevel("com.uade.crm", null);
            logging.setLogLevel("org.springframework.kafka", null);
        }
    }

    // Ayudas

    private Map<String, Object> evento(UUID lead, UUID cliente, String origen, String fecha, int puntos, String nivel,
            String observacion, List<String> motivos) {
        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("leadId", lead.toString());
        evento.put("clienteId", cliente.toString());
        evento.put("puntos", puntos);
        evento.put("nivel", nivel);
        evento.put("origen", origen);
        evento.put("fecha", fecha);
        evento.put("observacion", observacion);
        evento.put("motivos", motivos);
        return evento;
    }

    private void publicar(UUID clave, Map<String, Object> evento) {
        mensajes.publicar(topico, clave, escribir(evento));
    }

    private String escribir(Map<String, Object> evento) {
        try {
            return json.writeValueAsString(evento);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<CalificacionHistorica> esperarCalificaciones(UUID cliente, int cantidad) {
        await().atMost(ESPERA).until(() -> filas(cliente) >= cantidad);
        List<CalificacionHistorica> calificaciones = historial.delCliente(cliente);
        assertThat(calificaciones).hasSize(cantidad);
        return calificaciones;
    }

    private UUID cliente(String email) {
        return clientes.guardar(factory.crear(factory.solicitud("Martina", "López", email, null,
                Provincia.BUENOS_AIRES, null, CanalOrigen.INSTAGRAM))).id();
    }

    private int filas(UUID cliente) {
        return jdbc.queryForObject("SELECT count(*) FROM historial_calificacion WHERE cliente_id = ?", Integer.class,
                cliente);
    }

    /**
     * Hace fallar los primeros {@code veces} intentos de registrar una calificación del lead. La secuencia cuenta los
     * intentos porque {@code nextval} no se revierte con el rollback de la transacción que falla.
     */
    private void fallarAlRegistrar(UUID lead, int veces) {
        jdbc.execute("CREATE SEQUENCE prueba_fallas");
        jdbc.execute("""
                CREATE FUNCTION prueba_falla() RETURNS trigger AS $$
                BEGIN
                    IF NEW.lead_id = '%s' AND nextval('prueba_fallas') <= %d THEN
                        RAISE EXCEPTION 'falla de prueba';
                    END IF;
                    RETURN NEW;
                END
                $$ LANGUAGE plpgsql""".formatted(lead, veces));
        jdbc.execute("""
                CREATE TRIGGER prueba_falla BEFORE INSERT ON historial_calificacion
                FOR EACH ROW EXECUTE FUNCTION prueba_falla()""");
    }

    /** Intentos de registrar la calificación del lead del trigger, fallidos o no: cada uno llama una vez a nextval. */
    private long intentos() {
        return jdbc.queryForObject("SELECT last_value FROM prueba_fallas", Long.class);
    }

    private static void dormir(Duration duracion) {
        try {
            Thread.sleep(duracion.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
