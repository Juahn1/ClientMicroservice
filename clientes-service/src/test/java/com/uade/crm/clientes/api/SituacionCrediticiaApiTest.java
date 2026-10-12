package com.uade.crm.clientes.api;

import static com.uade.crm.clientes.api.AltaClienteApiTest.CLAVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uade.crm.clientes.CuilDePrueba;
import com.uade.crm.clientes.PruebaIntegracion;
import com.uade.crm.clientes.domain.port.SituacionCrediticiaPort;
import com.uade.crm.clientes.infrastructure.precalificacion.FuenteDePrueba;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Requirements de clientes-situacion-crediticia sobre el endpoint real, y los escenarios de la situación crediticia
 * de "Acceso restringido a servicios autorizados" (clientes-transversal). La fuente es una {@link FuenteDePrueba}
 * envuelta por la caché y la resiliencia reales (design D10 del change 05).
 */
@PruebaIntegracion
@ExtendWith(OutputCaptureExtension.class)
class SituacionCrediticiaApiTest {

    /** La de application-test.yml. */
    static final String CLAVE_CALIFICACION = "solo-test-clave-de-servicio-calificacion-0001";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    @Qualifier("fuenteSituacionCrediticia")
    SituacionCrediticiaPort fuenteBean;

    @Autowired
    CircuitBreakerRegistry circuitos;

    FuenteDePrueba fuente;
    CircuitBreaker circuito;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM situacion_crediticia");
        jdbc.update("DELETE FROM consentimiento");
        jdbc.update("DELETE FROM historial_calificacion");
        jdbc.update("DELETE FROM cliente");
        fuente = (FuenteDePrueba) fuenteBean;
        fuente.restablecer();
        circuito = circuitos.circuitBreaker("situacionCrediticia");
        circuito.reset();
    }

    /** El contexto es compartido: otras clases consultan la situación crediticia con la fuente normal. */
    @AfterEach
    void restablecer() {
        fuente.restablecer();
        circuito.reset();
    }

    // --- Consulta de la situación crediticia

    @Test
    void consultaConDatos() throws Exception {
        UUID id = alta("27-30111222-5");

        consultar(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(id.toString()))
                .andExpect(jsonPath("$.estado").value("CON_DATOS"))
                .andExpect(jsonPath("$.situacion").value(1))
                .andExpect(jsonPath("$.fuente").value("SIMULADA"))
                .andExpect(jsonPath("$.periodo").value(mesAnterior()))
                .andExpect(jsonPath("$.consultadaEn").isString());
    }

    @Test
    void sinRegistrosEnLaFuente() throws Exception {
        UUID id = alta("20-28555449-8");

        consultar(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SIN_REGISTROS"))
                .andExpect(jsonPath("$.situacion").value(nullValue()))
                .andExpect(jsonPath("$.fuente").value("SIMULADA"))
                .andExpect(jsonPath("$.periodo").value(mesAnterior()))
                .andExpect(jsonPath("$.consultadaEn").isString());
    }

    @Test
    void clienteSinCuil() throws Exception {
        UUID id = alta(null);

        consultar(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SIN_CUIL"))
                .andExpect(jsonPath("$.situacion").value(nullValue()))
                .andExpect(jsonPath("$.periodo").value(nullValue()))
                .andExpect(jsonPath("$.fuente").value(nullValue()))
                .andExpect(jsonPath("$.consultadaEn").value(nullValue()));
        assertThat(fuente.llamadas()).isZero();
    }

    // --- Situación de un cliente inexistente o anonimizado

    @Test
    void clienteInexistente() throws Exception {
        consultar(UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CLIENTE_NO_ENCONTRADO"));
    }

    @Test
    void clienteAnonimizado() throws Exception {
        UUID id = alta("27-30111222-5");
        mvc.perform(post("/clientes/{id}/anonimizacion", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"martina@gmail.com\" }"))
                .andExpect(status().isNoContent());

        consultar(id)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("CLIENTE_ANONIMIZADO"));
        assertThat(fuente.llamadas()).isZero();
    }

    // --- El CUIL no sale del servicio en la consulta

    @Test
    void respuestaSinCuil() throws Exception {
        UUID id = alta("20-12345678-6");

        String cuerpo = consultar(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.situacion").value(5))
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(cuerpo).has("cuil")).isFalse();
        assertThat(cuerpo).doesNotContain("20123456786").doesNotContain("20-12345678-6");
    }

    @Test
    void logsSinCuil(CapturedOutput salida) throws Exception {
        UUID id = alta("20-12345678-6");
        LoggingSystem logging = LoggingSystem.get(getClass().getClassLoader());
        try {
            logging.setLogLevel("com.uade.crm", LogLevel.DEBUG);
            logging.setLogLevel("org.springframework.web", LogLevel.DEBUG);
            consultar(id).andExpect(status().isOk());
        } finally {
            logging.setLogLevel("com.uade.crm", null);
            logging.setLogLevel("org.springframework.web", null);
        }

        assertThat(fuente.llamadas()).isEqualTo(1);
        assertThat(salida.getAll())
                .contains("Situación crediticia del cliente " + id)
                .doesNotContain("20123456786", "20-12345678-6")
                .doesNotContain(CuilDePrueba.protegido("20-12345678-6").hash());
    }

    // --- Caché con vigencia

    @Test
    void resultadoVigente() throws Exception {
        UUID id = alta("27-30111222-5");

        String primera = cuerpo(consultar(id));
        String segunda = cuerpo(consultar(id));

        assertThat(fuente.llamadas()).isEqualTo(1);
        assertThat(json.readTree(segunda)).isEqualTo(json.readTree(primera));
    }

    @Test
    void resultadoVencido() throws Exception {
        UUID id = alta("27-30111222-5");
        JsonNode primera = json.readTree(cuerpo(consultar(id)));
        jdbc.update("UPDATE situacion_crediticia SET vigente_hasta = now() - interval '1 second', "
                + "consultada_en = consultada_en - interval '31 days' WHERE cliente_id = ?", id);

        JsonNode segunda = json.readTree(cuerpo(consultar(id)));

        assertThat(fuente.llamadas()).isEqualTo(2);
        assertThat(segunda.get("consultadaEn")).isNotEqualTo(primera.get("consultadaEn"));
        assertThat(jdbc.queryForObject("SELECT vigente_hasta > now() FROM situacion_crediticia WHERE cliente_id = ?",
                Boolean.class, id)).isTrue();
    }

    @Test
    void unResultadoNoDisponibleNoSeGuarda() throws Exception {
        UUID id = alta("27-30111222-5");
        fuente.caer();
        consultar(id).andExpect(jsonPath("$.estado").value("NO_DISPONIBLE"));
        assertThat(filasEnCache(id)).isZero();

        fuente.restablecer();
        consultar(id).andExpect(jsonPath("$.estado").value("CON_DATOS"));

        assertThat(fuente.llamadas()).isEqualTo(1);
        assertThat(filasEnCache(id)).isEqualTo(1);
    }

    @Test
    void consultasSimultaneasSinResultadoGuardado() throws Exception {
        UUID id = alta("20-41000006-8");
        CyclicBarrier largada = new CyclicBarrier(2);
        Callable<String> consulta = () -> {
            largada.await(5, TimeUnit.SECONDS);
            return cuerpo(consultar(id));
        };
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<String> una = hilos.submit(consulta);
            Future<String> otra = hilos.submit(consulta);

            JsonNode primera = json.readTree(una.get(10, TimeUnit.SECONDS));
            JsonNode segunda = json.readTree(otra.get(10, TimeUnit.SECONDS));
            assertThat(primera.get("estado").asText()).isEqualTo("CON_DATOS").isEqualTo(segunda.get("estado").asText());
            assertThat(primera.get("situacion").asInt()).isEqualTo(2).isEqualTo(segunda.get("situacion").asInt());
        } finally {
            hilos.shutdownNow();
        }
        assertThat(filasEnCache(id)).isEqualTo(1);
    }

    // --- Degradación ante fallas de la fuente

    @Test
    void fuenteCaida() throws Exception {
        UUID id = alta("27-30111222-5");
        fuente.caer();

        consultar(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NO_DISPONIBLE"))
                .andExpect(jsonPath("$.situacion").value(nullValue()));
    }

    @Test
    void fuenteLenta() throws Exception {
        UUID id = alta("27-30111222-5");
        fuente.demorar(Duration.ofSeconds(5));
        long inicio = System.nanoTime();

        consultar(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NO_DISPONIBLE"));

        // Timeout de 300 ms en application-test.yml; la demora es de 5 s.
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofMillis(1500));
    }

    @Test
    void circuitoAbiertoYReintentoDespuesDeLaVentana() throws Exception {
        UUID id = alta("27-30111222-5");
        fuente.caer();
        for (int i = 0; i < 4; i++) {
            consultar(id).andExpect(jsonPath("$.estado").value("NO_DISPONIBLE"));
        }
        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        fuente.restablecer();

        consultar(id).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("NO_DISPONIBLE"));
        assertThat(fuente.llamadas()).isZero();

        // Ventana de espera de 500 ms en application-test.yml.
        Thread.sleep(700);
        consultar(id).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CON_DATOS"));
        assertThat(fuente.llamadas()).isEqualTo(1);
    }

    // --- Acceso restringido a servicios autorizados (endpoint real)

    @Test
    void sinClaveOConLaDeLeadsResponde401SinConsultarLaFuente() throws Exception {
        UUID id = alta("27-30111222-5");

        mvc.perform(get("/clientes/{id}/situacion-crediticia", id))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CLAVE_SERVICIO_INVALIDA"));
        mvc.perform(get("/clientes/{id}/situacion-crediticia", id).header("X-Api-Key", CLAVE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CLAVE_SERVICIO_INVALIDA"));
        assertThat(fuente.llamadas()).isZero();
    }

    @Test
    void laClaveDeCalificacionNoApareceEnLosLogs(CapturedOutput salida) throws Exception {
        consultar(alta("27-30111222-5")).andExpect(status().isOk());

        assertThat(salida.getAll()).doesNotContain(CLAVE_CALIFICACION);
    }

    // --- OpenAPI

    @Test
    void elContratoDocumentaElEndpointYSusEstados() throws Exception {
        JsonNode contrato = json.readTree(cuerpo(mvc.perform(get("/v3/api-docs"))));

        JsonNode operacion = contrato.at("/paths/~1clientes~1{id}~1situacion-crediticia/get");
        assertThat(operacion.isMissingNode()).isFalse();
        assertThat(operacion.at("/responses").has("401")).isTrue();
        assertThat(operacion.at("/responses").has("410")).isTrue();
        assertThat(contrato.at("/components/schemas/SituacionCrediticiaResponse/properties/estado/enum").toString())
                .contains("CON_DATOS", "SIN_REGISTROS", "SIN_CUIL", "SIN_CONSENTIMIENTO", "NO_DISPONIBLE");
    }

    private UUID alta(String cuil) throws Exception {
        String respuesta = mvc.perform(post("/clientes").header("X-Api-Key", CLAVE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AltaConCuilApiTest.cuerpo("martina@gmail.com", null, cuil, cuil == null ? null : true)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(respuesta).get("clienteId").asText());
    }

    private ResultActions consultar(UUID id) throws Exception {
        return mvc.perform(get("/clientes/{id}/situacion-crediticia", id).header("X-Api-Key", CLAVE_CALIFICACION));
    }

    private static String cuerpo(ResultActions resultado) throws Exception {
        return resultado.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private int filasEnCache(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM situacion_crediticia WHERE cliente_id = ?", Integer.class, id);
    }

    private static String mesAnterior() {
        return YearMonth.now(ZoneOffset.UTC).minusMonths(1).format(DateTimeFormatter.ofPattern("yyyyMM"));
    }
}
