package com.uade.crm.clientes.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uade.crm.clientes.CuilDePrueba;
import com.uade.crm.clientes.PostgresDePrueba;
import com.uade.crm.clientes.domain.ClienteFactory;
import com.uade.crm.clientes.domain.model.CalificacionHistorica;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Historial de calificaciones sobre PostgreSQL real (design D4 del change 08). Sin la transacción de test: cada
 * registro corre en su propia transacción, como cuando lo llama el consumidor.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresDePrueba.class, ClienteRepositorioJpa.class, HistorialCalificacionesJpa.class})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HistorialCalificacionesJpaTest {

    private static final Instant DIEZ = Instant.parse("2026-10-11T13:00:00Z");
    private static final Instant REGISTRADA = Instant.parse("2026-10-11T20:00:00Z");

    private final ClienteFactory factory = CuilDePrueba.factory(Clock.systemUTC());

    @Autowired
    HistorialCalificacionesJpa historial;

    @Autowired
    ClienteRepositorioJpa clientes;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM historial_calificacion");
        jdbc.update("DELETE FROM consentimiento");
        jdbc.update("DELETE FROM cliente");
    }

    @Test
    void registraUnaCalificacionDeUnClienteActivo() {
        UUID clienteId = cliente("martina@gmail.com");
        CalificacionHistorica calificacion = calificacion(clienteId, UUID.randomUUID(), "AUTOMATICA", DIEZ,
                List.of("[REGLA_COBERTURA]: +60", "[REGLA_FINANCIACION]: 0", "[REGLA_UBICACION]: 0"));

        assertThat(historial.registrar(calificacion, REGISTRADA)).isTrue();

        assertThat(historial.delCliente(clienteId)).containsExactly(calificacion);
    }

    @Test
    void elMismoRegistroDosVecesDejaUnaFila() {
        UUID clienteId = cliente("martina@gmail.com");
        CalificacionHistorica calificacion = calificacion(clienteId, UUID.randomUUID(), "AUTOMATICA", DIEZ, List.of());

        assertThat(historial.registrar(calificacion, REGISTRADA)).isTrue();
        assertThat(historial.registrar(calificacion, REGISTRADA.plusSeconds(5))).isFalse();

        assertThat(filas(clienteId)).isEqualTo(1);
    }

    @Test
    void noRegistraParaUnClienteAnonimizado() {
        UUID clienteId = cliente("martina@gmail.com");
        Cliente cliente = clientes.buscarPorId(clienteId).orElseThrow();
        cliente.anonimizar(DIEZ);
        clientes.guardar(cliente);

        assertThat(historial.registrar(calificacion(clienteId, UUID.randomUUID(), "AUTOMATICA", DIEZ, List.of()),
                REGISTRADA)).isFalse();

        assertThat(filas(clienteId)).isZero();
    }

    @Test
    void noRegistraParaUnClienteInexistente() {
        UUID inexistente = UUID.randomUUID();

        assertThat(historial.registrar(calificacion(inexistente, UUID.randomUUID(), "AUTOMATICA", DIEZ, List.of()),
                REGISTRADA)).isFalse();

        assertThat(filas(inexistente)).isZero();
    }

    @Test
    void ordenaDeLaMasRecienteALaMasViejaYConservaElOrdenDeLosMotivos() {
        UUID clienteId = cliente("martina@gmail.com");
        UUID leadA = UUID.randomUUID();
        UUID leadB = UUID.randomUUID();
        CalificacionHistorica automaticaA = calificacion(clienteId, leadA, "AUTOMATICA", DIEZ, List.of("z", "a", "m"));
        CalificacionHistorica automaticaB = calificacion(clienteId, leadB, "AUTOMATICA", DIEZ.plusSeconds(3600),
                List.of());
        CalificacionHistorica manualA = calificacion(clienteId, leadA, "MANUAL", DIEZ.plusSeconds(7200), List.of());
        historial.registrar(automaticaA, REGISTRADA);
        historial.registrar(manualA, REGISTRADA);
        historial.registrar(automaticaB, REGISTRADA);

        List<CalificacionHistorica> leidas = historial.delCliente(clienteId);

        assertThat(leidas).containsExactly(manualA, automaticaB, automaticaA);
        assertThat(leidas.get(2).motivos()).containsExactly("z", "a", "m");
    }

    @Test
    void conservaLaObservacionYLosNegativos() {
        UUID clienteId = cliente("martina@gmail.com");
        CalificacionHistorica manual = new CalificacionHistorica(UUID.randomUUID(), clienteId, "NO_POTABLE", -25,
                "MANUAL", DIEZ, "Anticipo no confirmado", List.of());

        historial.registrar(manual, REGISTRADA);

        assertThat(historial.delCliente(clienteId)).containsExactly(manual);
    }

    @Test
    void eliminarDelClienteBorraSoloSusFilas() {
        UUID martina = cliente("martina@gmail.com");
        UUID diego = cliente("diego@gmail.com");
        historial.registrar(calificacion(martina, UUID.randomUUID(), "AUTOMATICA", DIEZ, List.of()), REGISTRADA);
        historial.registrar(calificacion(diego, UUID.randomUUID(), "AUTOMATICA", DIEZ, List.of()), REGISTRADA);

        historial.eliminarDelCliente(martina);
        historial.eliminarDelCliente(martina);

        assertThat(filas(martina)).isZero();
        assertThat(filas(diego)).isEqualTo(1);
    }

    @Test
    void dosRegistrosSimultaneosDelMismoEventoDejanUnaSolaFila() throws Exception {
        UUID clienteId = cliente("martina@gmail.com");
        CalificacionHistorica calificacion = calificacion(clienteId, UUID.randomUUID(), "AUTOMATICA", DIEZ, List.of());
        CyclicBarrier largada = new CyclicBarrier(2);
        Runnable registrar = () -> {
            try {
                largada.await(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            historial.registrar(calificacion, REGISTRADA);
        };

        CompletableFuture.allOf(CompletableFuture.runAsync(registrar), CompletableFuture.runAsync(registrar))
                .get(10, TimeUnit.SECONDS);

        assertThat(filas(clienteId)).isEqualTo(1);
    }

    @Test
    void laBaseRechazaMotivosQueNoSonUnArray() {
        UUID clienteId = cliente("martina@gmail.com");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO historial_calificacion
                    (id, cliente_id, lead_id, nivel, puntaje, origen, calculada_en, motivos, registrada_en)
                VALUES (?, ?, ?, 'POTABLE', 35, 'AUTOMATICA', now(), '{"regla": "x"}', now())""",
                UUID.randomUUID(), clienteId, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_historial_motivos");
    }

    private CalificacionHistorica calificacion(UUID clienteId, UUID leadId, String origen, Instant calculadaEn,
            List<String> motivos) {
        return new CalificacionHistorica(leadId, clienteId, "MUY_POTABLE", 60, origen,
                calculadaEn.truncatedTo(ChronoUnit.MICROS), null, motivos);
    }

    private UUID cliente(String email) {
        return clientes.guardar(factory.crear(factory.solicitud("Martina", "López", email, null,
                Provincia.BUENOS_AIRES, null, CanalOrigen.INSTAGRAM))).id();
    }

    private int filas(UUID clienteId) {
        return jdbc.queryForObject("SELECT count(*) FROM historial_calificacion WHERE cliente_id = ?", Integer.class,
                clienteId);
    }
}
