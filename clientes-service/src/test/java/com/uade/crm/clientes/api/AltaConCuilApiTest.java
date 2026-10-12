package com.uade.crm.clientes.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uade.crm.clientes.CuilDePrueba;
import com.uade.crm.clientes.PruebaIntegracion;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.Consentimiento;
import com.uade.crm.clientes.domain.model.FinalidadConsentimiento;
import com.uade.crm.clientes.domain.model.MotivoRevision;
import com.uade.crm.clientes.domain.port.ClienteRepositorio;
import com.uade.crm.clientes.domain.valor.CuilProtegido;
import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.clientes.domain.valor.Telefono;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Requirements de {@code clientes-privacidad} sobre el alta: validación del CUIL, consentimiento de precalificación,
 * deduplicación por CUIL y marcas de revisión (design D4 y D5 del change 04). Las altas simultáneas con el mismo CUIL
 * se prueban en {@code AltaClienteServiceTest}.
 */
@PruebaIntegracion
@ExtendWith(OutputCaptureExtension.class)
class AltaConCuilApiTest {

    private static final String CUIL_MARTINA = "27-30111222-5";
    private static final String CUIL_OTRO = "20-12345678-6";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ClienteRepositorio repositorio;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM situacion_crediticia");
        jdbc.update("DELETE FROM consentimiento");
        jdbc.update("DELETE FROM historial_calificacion");
        jdbc.update("DELETE FROM cliente");
    }

    // --- Validación del CUIL

    @Test
    void cuilValidoConGuiones() throws Exception {
        UUID id = idDe(alta(cuerpo("martina@gmail.com", "11 1234-5678", "20-12345678-6", true))
                .andExpect(status().isCreated()));

        assertThat(cliente(id).cuil()).get().extracting(CuilProtegido::enmascarado).isEqualTo("XX-XXXX5678-X");
    }

    @Test
    void cuilValidoSinGuiones() throws Exception {
        alta(cuerpo("martina@gmail.com", null, "27301112225", true)).andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"20-12345678-5", "30-71234567-1", "20-1234567-6"})
    void cuilInvalido(String cuil) throws Exception {
        alta(cuerpo("martina@gmail.com", null, cuil, true))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CUIL_INVALIDO"))
                .andExpect(jsonPath("$.errores[0].campo").value("cuil"));

        assertThat(clientes()).isZero();
    }

    // --- El CUIL requiere consentimiento de precalificación

    @Test
    void cuilSinConsentimiento() throws Exception {
        alta(cuerpo("martina@gmail.com", null, CUIL_OTRO, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CONSENTIMIENTO_REQUERIDO"))
                .andExpect(jsonPath("$.errores[0].campo").value("consentimiento"));

        assertThat(clientes()).isZero();
    }

    @Test
    void cuilConConsentimientoRechazado() throws Exception {
        alta(cuerpo("martina@gmail.com", null, CUIL_OTRO, false))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CONSENTIMIENTO_REQUERIDO"));
    }

    @Test
    void consentimientoSinCuil() throws Exception {
        alta(cuerpo("martina@gmail.com", null, null, true))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("consentimiento"));

        assertThat(clientes()).isZero();
    }

    // --- Registro del consentimiento de precalificación

    @Test
    void consentimientoRegistrado() throws Exception {
        UUID id = idDe(alta(cuerpo("martina@gmail.com", null, CUIL_MARTINA, true)));

        assertThat(cliente(id).consentimientosVigentes()).singleElement().satisfies(c -> {
            assertThat(c.finalidad()).isEqualTo(FinalidadConsentimiento.PRECALIFICACION_CREDITICIA);
            assertThat(c.versionTexto()).isEqualTo("v1");
            assertThat(c.otorgadoEn()).isNotNull();
        });
    }

    @Test
    void recontactoConElConsentimientoYaVigente() throws Exception {
        UUID id = idDe(alta(cuerpo("martina@gmail.com", null, CUIL_MARTINA, true)));
        Instant otorgado = cliente(id).consentimientosVigentes().get(0).otorgadoEn();

        alta(cuerpo("martina@gmail.com", null, CUIL_MARTINA, true)).andExpect(status().isOk());

        assertThat(cliente(id).consentimientosVigentes()).singleElement()
                .extracting(Consentimiento::otorgadoEn).isEqualTo(otorgado);
        assertThat(consentimientos()).isEqualTo(1);
    }

    @Test
    void recontactoSinCuil() throws Exception {
        UUID id = idDe(alta(cuerpo("martina@gmail.com", null, CUIL_MARTINA, true)));

        alta(cuerpo("martina@gmail.com", null, null, null)).andExpect(status().isOk());

        Cliente martina = cliente(id);
        assertThat(martina.cuil()).isPresent();
        assertThat(martina.consentimientosVigentes()).hasSize(1);
    }

    // --- Deduplicación por CUIL

    @Test
    void mismoCuilConOtroEmail() throws Exception {
        UUID id = idDe(alta(cuerpo("martina@gmail.com", "11 1234-5678", CUIL_MARTINA, true)));

        alta(cuerpo("martu.lopez@gmail.com", "11 8765-4321", CUIL_MARTINA, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(id.toString()))
                .andExpect(jsonPath("$.nuevo").value(false))
                .andExpect(jsonPath("$.coincidencia").value("CUIL"));

        Cliente martina = cliente(id);
        assertThat(martina.email()).isEqualTo(Email.de("martina@gmail.com"));
        assertThat(martina.telefono()).contains(Telefono.deE164("+5491112345678"));
        assertThat(martina.telefonoAlternativo()).contains(Telefono.deE164("+5491187654321"));
        assertThat(martina.contactosRegistrados()).isEqualTo(2);
        assertThat(martina.requiereRevision()).isFalse();
        assertThat(clientes()).isEqualTo(1);
    }

    @Test
    void mismoCuilYMismoEmail() throws Exception {
        UUID id = idDe(alta(cuerpo("martina@gmail.com", null, CUIL_MARTINA, true)));

        alta(cuerpo("martina@gmail.com", null, CUIL_MARTINA, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(id.toString()))
                .andExpect(jsonPath("$.coincidencia").value("CUIL"));

        assertThat(cliente(id).requiereRevision()).isFalse();
    }

    @Test
    void elCuilQuedaLibreAlAnonimizar() throws Exception {
        UUID id = idDe(alta(cuerpo("martina@gmail.com", null, CUIL_OTRO, true)));
        mvc.perform(post("/clientes/{id}/anonimizacion", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"martina@gmail.com\" }"))
                .andExpect(status().isNoContent());

        UUID nuevo = idDe(alta(cuerpo("otra@gmail.com", null, CUIL_OTRO, true)).andExpect(status().isCreated()));

        assertThat(nuevo).isNotEqualTo(id);
    }

    // --- Registro del CUIL en un recontacto por email

    @Test
    void clienteQueVuelvePidiendoFinanciacion() throws Exception {
        UUID id = idDe(alta(cuerpo("martina@gmail.com", null, null, null)));

        alta(cuerpo("martina@gmail.com", null, CUIL_OTRO, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(id.toString()))
                .andExpect(jsonPath("$.coincidencia").value("EMAIL"));

        Cliente martina = cliente(id);
        assertThat(martina.cuil()).get().extracting(CuilProtegido::enmascarado).isEqualTo("XX-XXXX5678-X");
        assertThat(martina.consentimientosVigentes()).hasSize(1);
    }

    // --- Un CUIL distinto no reemplaza al registrado

    @Test
    void mismoEmailOtroCuil() throws Exception {
        UUID id = idDe(alta(cuerpo("martina@gmail.com", null, CUIL_MARTINA, true)));

        alta(cuerpo("martina@gmail.com", null, CUIL_OTRO, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(id.toString()))
                .andExpect(jsonPath("$.coincidencia").value("EMAIL"));

        Cliente martina = cliente(id);
        assertThat(martina.cuil()).get().extracting(CuilProtegido::enmascarado).isEqualTo("XX-XXXX1222-X");
        assertThat(martina.requiereRevision()).isTrue();
        assertThat(martina.motivoRevision()).contains(MotivoRevision.CUIL_DISTINTO);
        assertThat(consentimientos()).isEqualTo(1);
    }

    // --- Posible duplicado entre dos clientes

    @Test
    void cuilDeUnClienteYEmailDeOtro() throws Exception {
        UUID a = idDe(alta(cuerpo("martina@gmail.com", null, CUIL_MARTINA, true)));
        UUID b = idDe(alta(cuerpo("martu.lopez@gmail.com", null, null, null)));
        Cliente bAntes = cliente(b);

        alta(cuerpo("martu.lopez@gmail.com", null, CUIL_MARTINA, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(a.toString()))
                .andExpect(jsonPath("$.coincidencia").value("CUIL"));

        assertThat(cliente(a).motivoRevision()).contains(MotivoRevision.POSIBLE_DUPLICADO);
        Cliente bDespues = cliente(b);
        assertThat(bDespues.version()).isEqualTo(bAntes.version());
        assertThat(bDespues.requiereRevision()).isFalse();
        assertThat(bDespues.cuil()).isEmpty();
    }

    // --- Se conserva el primer motivo de revisión

    @Test
    void segundaMarcaSobreUnClienteYaMarcado() throws Exception {
        UUID a = idDe(alta(cuerpo("martina@gmail.com", null, CUIL_MARTINA, true)));
        alta(cuerpo("martina@gmail.com", null, CUIL_OTRO, true));
        idDe(alta(cuerpo("martu.lopez@gmail.com", null, null, null)));

        alta(cuerpo("martu.lopez@gmail.com", null, CUIL_MARTINA, true)).andExpect(status().isOk());

        assertThat(cliente(a).motivoRevision()).contains(MotivoRevision.CUIL_DISTINTO);
    }

    // --- Protección del CUIL

    /** Escenario "CUIL en la base": se recorren todas las columnas de las dos tablas, cualquiera sea su tipo. */
    @Test
    void elCuilNoQuedaEnClaroEnLaBase() throws Exception {
        alta(cuerpo("martina@gmail.com", null, "20-12345678-6", true)).andExpect(status().isCreated());

        var columnas = jdbc.queryForList("SELECT table_name, column_name FROM information_schema.columns "
                + "WHERE table_schema = current_schema() AND table_name IN ('cliente', 'consentimiento')");
        assertThat(columnas).hasSizeGreaterThan(20);
        for (var columna : columnas) {
            String sql = "SELECT count(*) FROM %s WHERE %s::text LIKE '%%20123456786%%'"
                    .formatted(columna.get("table_name"), columna.get("column_name"));
            assertThat(jdbc.queryForObject(sql, Integer.class)).as(columna.toString()).isZero();
        }
    }

    /** Requirement "Datos personales fuera de los logs" sobre el alta real, con DEBUG en la app y en Spring MVC. */
    @Test
    void elAltaConCuilNoDejaElCuilNiSuHashEnLosLogs(CapturedOutput salida) throws Exception {
        LoggingSystem logging = LoggingSystem.get(getClass().getClassLoader());
        try {
            logging.setLogLevel("com.uade.crm", LogLevel.DEBUG);
            logging.setLogLevel("org.springframework.web", LogLevel.DEBUG);
            alta(cuerpo("martina@gmail.com", "11 1234-5678", "20-12345678-6", true)).andExpect(status().isCreated());
            alta(cuerpo("martina@gmail.com", null, "27-30111222-5", true)).andExpect(status().isOk());
        } finally {
            logging.setLogLevel("com.uade.crm", null);
            logging.setLogLevel("org.springframework.web", null);
        }

        assertThat(salida.getAll())
                .contains("Alta del cliente")
                .doesNotContain("20123456786", "20-12345678-6", "27301112225", "27-30111222-5")
                .doesNotContain(CuilDePrueba.protegido("20-12345678-6").hash())
                .doesNotContain(CuilDePrueba.protegido("27-30111222-5").hash());
    }

    private ResultActions alta(String cuerpo) throws Exception {
        return mvc.perform(post("/clientes").header("X-Api-Key", AltaClienteApiTest.CLAVE)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private UUID idDe(ResultActions resultado) throws Exception {
        return UUID.fromString(json.readTree(resultado.andReturn().getResponse().getContentAsString())
                .get("clienteId").asText());
    }

    private Cliente cliente(UUID id) {
        return repositorio.buscarPorId(id).orElseThrow();
    }

    private int clientes() {
        return jdbc.queryForObject("SELECT count(*) FROM cliente", Integer.class);
    }

    private int consentimientos() {
        return jdbc.queryForObject("SELECT count(*) FROM consentimiento", Integer.class);
    }

    static String cuerpo(String email, String telefono, String cuil, Boolean consentimiento) {
        return """
                { "nombre": "Martina", "apellido": "López", "email": "%s", "telefono": %s,
                  "provincia": "BUENOS_AIRES", "cuil": %s, "consentimiento": %s }
                """.formatted(email, texto(telefono), texto(cuil), consentimiento);
    }

    private static String texto(String valor) {
        return valor == null ? "null" : "\"" + valor + "\"";
    }
}
