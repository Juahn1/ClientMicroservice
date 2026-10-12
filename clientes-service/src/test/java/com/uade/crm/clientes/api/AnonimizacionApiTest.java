package com.uade.crm.clientes.api;

import static com.uade.crm.clientes.api.AltaClienteApiTest.CLAVE;
import static com.uade.crm.clientes.api.AltaClienteApiTest.martina;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uade.crm.clientes.MensajesKafka;
import com.uade.crm.clientes.PruebaIntegracion;
import com.uade.crm.clientes.domain.model.CalificacionHistorica;
import com.uade.crm.clientes.domain.port.HistorialCalificaciones;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Requirements: Anonimización a pedido del titular y Confirmación de la anonimización (clientes-gestion), la
 * publicación de ClienteAnonimizado (clientes-eventos) y "Escrituras del personal sin clave" (clientes-transversal).
 */
@PruebaIntegracion
@Import(MensajesKafka.class)
@ExtendWith(OutputCaptureExtension.class)
class AnonimizacionApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MensajesKafka mensajes;

    @Autowired
    HistorialCalificaciones historial;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM situacion_crediticia");
        jdbc.update("DELETE FROM consentimiento");
        jdbc.update("DELETE FROM historial_calificacion");
        jdbc.update("DELETE FROM cliente");
    }

    @Test
    void anonimizacion() throws Exception {
        UUID id = alta(martina("martina@gmail.com", "11 1234-5678", "INSTAGRAM"));

        anonimizar(id, "martina@gmail.com").andExpect(status().isNoContent());

        mvc.perform(get("/clientes/{id}", id))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("CLIENTE_ANONIMIZADO"));
        mvc.perform(get("/clientes").param("q", "martina")).andExpect(jsonPath("$.contenido", hasSize(0)));
        mvc.perform(get("/clientes")).andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void datosConservadosYEliminados() throws Exception {
        UUID id = alta(martina("martina@gmail.com", "11 1234-5678", "INSTAGRAM"));
        alta(martina("martina@gmail.com", "351 412-3456", "WHATSAPP"));
        jdbc.update("UPDATE cliente SET localidad = 'Lanús' WHERE id = ?", id);

        anonimizar(id, "martina@gmail.com").andExpect(status().isNoContent());

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM cliente WHERE id = ?", id);
        assertThat(fila.get("id")).isEqualTo(id);
        assertThat(fila.get("provincia")).isEqualTo("BUENOS_AIRES");
        assertThat(fila.get("canal_origen")).isEqualTo("INSTAGRAM");
        assertThat(fila.get("ultimo_canal")).isEqualTo("WHATSAPP");
        assertThat(fila.get("contactos_registrados")).isEqualTo(2);
        assertThat(fila.get("creado_en")).isNotNull();
        assertThat(fila.get("anonimizado_en")).isNotNull();
        assertThat(fila.get("estado")).isEqualTo("ANONIMIZADO");
        assertThat(fila.get("nombre")).isEqualTo("ANONIMIZADO");
        assertThat(fila.get("apellido")).isEqualTo("ANONIMIZADO");
        assertThat(fila.get("email")).isNull();
        assertThat(fila.get("telefono_e164")).isNull();
        assertThat(fila.get("telefono_alternativo")).isNull();
        assertThat(fila.get("localidad")).isNull();
        assertThat(fila.values()).map(String::valueOf)
                .noneMatch(valor -> valor.contains("Martina") || valor.contains("López") || valor.contains("martina")
                        || valor.contains("1112345678") || valor.contains("Lanús"));
    }

    /** Change 04: "Datos conservados y eliminados" con CUIL y "Consentimientos revocados". */
    @Test
    void anonimizarBorraElCuilYRevocaLosConsentimientos() throws Exception {
        UUID id = alta(AltaConCuilApiTest.cuerpo("martina@gmail.com", null, "20-12345678-6", true));

        anonimizar(id, "martina@gmail.com").andExpect(status().isNoContent());

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM cliente WHERE id = ?", id);
        assertThat(fila.get("cuil_cifrado")).isNull();
        assertThat(fila.get("cuil_hash")).isNull();
        assertThat(fila.get("cuil_ultimos4")).isNull();
        Map<String, Object> consentimiento = jdbc.queryForMap("SELECT * FROM consentimiento WHERE cliente_id = ?", id);
        assertThat(consentimiento.get("revocado_en")).isEqualTo(fila.get("anonimizado_en"));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM consentimiento WHERE cliente_id = ? AND revocado_en IS NULL", Integer.class, id))
                .isZero();
    }

    /** Change 05: "Situación crediticia eliminada". */
    @Test
    void anonimizarBorraLaSituacionCrediticiaGuardada() throws Exception {
        UUID id = alta(AltaConCuilApiTest.cuerpo("martina@gmail.com", null, "20-12345678-6", true));
        consultarSituacion(id).andExpect(status().isOk());
        assertThat(situacionesGuardadas(id)).isEqualTo(1);

        anonimizar(id, "martina@gmail.com").andExpect(status().isNoContent());

        assertThat(situacionesGuardadas(id)).isZero();
    }

    /** Design D9 del change 05: si no se puede borrar la situación crediticia, la anonimización completa se revierte. */
    @Test
    void siFallaElBorradoDeLaSituacionLaAnonimizacionSeRevierte() throws Exception {
        UUID id = alta(AltaConCuilApiTest.cuerpo("martina@gmail.com", null, "20-12345678-6", true));
        consultarSituacion(id).andExpect(status().isOk());
        jdbc.execute("""
                CREATE FUNCTION prueba_impedir_borrado() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'borrado impedido por el test'; END $$""");
        jdbc.execute("CREATE TRIGGER prueba_impedir_borrado BEFORE DELETE ON situacion_crediticia "
                + "FOR EACH ROW EXECUTE FUNCTION prueba_impedir_borrado()");
        try {
            anonimizar(id, "martina@gmail.com").andExpect(status().isInternalServerError());
        } finally {
            jdbc.execute("DROP TRIGGER prueba_impedir_borrado ON situacion_crediticia");
            jdbc.execute("DROP FUNCTION prueba_impedir_borrado()");
        }

        assertThat(jdbc.queryForObject("SELECT estado FROM cliente WHERE id = ?", String.class, id)).isEqualTo("ACTIVO");
        assertThat(jdbc.queryForObject("SELECT cuil_hash IS NOT NULL FROM cliente WHERE id = ?", Boolean.class, id))
                .isTrue();
        assertThat(situacionesGuardadas(id)).isEqualTo(1);
        assertThat(mensajes.ninguno("cliente-anonimizado", id)).isEmpty();
    }

    /** Change 08: "Historial de calificaciones eliminado". */
    @Test
    void anonimizarBorraElHistorialDeCalificaciones() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));
        UUID otro = alta(martina("diego@gmail.com", null, null));
        calificar(id, "AUTOMATICA");
        calificar(id, "MANUAL");
        calificar(otro, "AUTOMATICA");

        anonimizar(id, "martina@gmail.com").andExpect(status().isNoContent());

        assertThat(calificaciones(id)).isZero();
        assertThat(calificaciones(otro)).isEqualTo(1);
    }

    /** Design D6 del change 08: si no se puede borrar el historial, la anonimización completa se revierte. */
    @Test
    void siFallaElBorradoDelHistorialLaAnonimizacionSeRevierte() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));
        calificar(id, "AUTOMATICA");
        jdbc.execute("""
                CREATE FUNCTION prueba_impedir_borrado() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'borrado impedido por el test'; END $$""");
        jdbc.execute("CREATE TRIGGER prueba_impedir_borrado BEFORE DELETE ON historial_calificacion "
                + "FOR EACH ROW EXECUTE FUNCTION prueba_impedir_borrado()");
        try {
            anonimizar(id, "martina@gmail.com").andExpect(status().isInternalServerError());
        } finally {
            jdbc.execute("DROP TRIGGER prueba_impedir_borrado ON historial_calificacion");
            jdbc.execute("DROP FUNCTION prueba_impedir_borrado()");
        }

        assertThat(jdbc.queryForObject("SELECT estado FROM cliente WHERE id = ?", String.class, id)).isEqualTo("ACTIVO");
        assertThat(calificaciones(id)).isEqualTo(1);
        assertThat(mensajes.ninguno("cliente-anonimizado", id)).isEmpty();
    }

    @Test
    void anonimizacionRepetidaNoCambiaNiPublica() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));
        anonimizar(id, "martina@gmail.com").andExpect(status().isNoContent());
        Object anonimizadoEn = jdbc.queryForObject("SELECT anonimizado_en FROM cliente WHERE id = ?", Object.class, id);

        anonimizar(id, "otro@gmail.com").andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT anonimizado_en FROM cliente WHERE id = ?", Object.class, id))
                .isEqualTo(anonimizadoEn);
        assertThat(mensajes.ninguno("cliente-anonimizado", id)).hasSize(1);
    }

    @Test
    void elEmailQuedaLibre() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));
        anonimizar(id, "martina@gmail.com").andExpect(status().isNoContent());

        String respuesta = mvc.perform(post("/clientes").header("X-Api-Key", CLAVE)
                        .contentType(MediaType.APPLICATION_JSON).content(martina("martina@gmail.com", null, null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(respuesta).get("clienteId").asText()).isNotEqualTo(id.toString());
    }

    @Test
    void anonimizacionDeUnClienteInexistente() throws Exception {
        anonimizar(UUID.randomUUID(), "martina@gmail.com")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CLIENTE_NO_ENCONTRADO"));
    }

    @Test
    void confirmacionConOtroFormato() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));

        anonimizar(id, " MARTINA@gmail.com ").andExpect(status().isNoContent());

        mvc.perform(get("/clientes/{id}", id)).andExpect(status().isGone());
    }

    @Test
    void confirmacionIncorrecta() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));

        anonimizar(id, "otra@gmail.com")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CONFIRMACION_INVALIDA"))
                .andExpect(jsonPath("$.errores[0].campo").value("email"));

        mvc.perform(get("/clientes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("martina@gmail.com"));
        assertThat(mensajes.ninguno("cliente-anonimizado", id)).isEmpty();
    }

    @Test
    void confirmacionAusente() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));

        mvc.perform(post("/clientes/{id}/anonimizacion", id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("email"));
    }

    /** Scenario de clientes-eventos: Evento de anonimización (y revisión del payload). */
    @Test
    void publicaClienteAnonimizadoSoloConElId() throws Exception {
        UUID id = alta(martina("martina@gmail.com", "11 1234-5678", null));

        mvc.perform(post("/clientes/{id}/anonimizacion", id).header("X-Correlation-Id", "baja-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"email\": \"martina@gmail.com\" }"))
                .andExpect(status().isNoContent());

        String mensaje = mensajes.esperar("cliente-anonimizado", id, 1).getFirst().value();
        JsonNode sobre = json.readTree(mensaje);
        assertThat(sobre.get("eventType").asText()).isEqualTo("ClienteAnonimizado");
        assertThat(sobre.get("correlationId").asText()).isEqualTo("baja-1");
        assertThat(sobre.get("payload").get("clienteId").asText()).isEqualTo(id.toString());
        assertThat(sobre.get("payload").size()).isEqualTo(1);
        assertThat(mensaje).doesNotContain("martina").doesNotContain("Martina").doesNotContain("1112345678");
    }

    /** Requirement: Datos personales fuera de los logs, sobre las escrituras del change 03. */
    @Test
    void losLogsDeEdicionYAnonimizacionNoContienenDatosPersonales(CapturedOutput salida) throws Exception {
        UUID id = alta(martina("martina@gmail.com", "11 1234-5678", null));
        mvc.perform(patch("/clientes/{id}", id).header("If-Match", "W/\"0\"").header("X-Usuario", "tm-ana")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nombre\": \"Martu\", \"email\": \"martu@gmail.com\", \"telefono\": \"351 412-3456\" }"))
                .andExpect(status().isOk());

        anonimizar(id, "martu@gmail.com").andExpect(status().isNoContent());

        assertThat(salida.getOut()).contains("Edición del cliente " + id).contains("Cliente " + id + " anonimizado")
                .contains("[tm-ana]");
        assertThat(salida.getAll())
                .doesNotContain("martina@gmail.com").doesNotContain("martu@gmail.com")
                .doesNotContain("Martu").doesNotContain("1112345678").doesNotContain("3514123456");
    }

    /** Scenario de clientes-transversal: Escrituras del personal sin clave. */
    @Test
    void lasEscriturasDelPersonalNoPidenClaveYElAltaSi() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));

        mvc.perform(patch("/clientes/{id}", id).header("If-Match", "W/\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nombre\": \"Martu\" }"))
                .andExpect(status().isOk());
        mvc.perform(put("/clientes/{id}/no-contactar", id)).andExpect(status().isNoContent());
        anonimizar(id, "martina@gmail.com").andExpect(status().isNoContent());

        mvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content(martina("otra@gmail.com", null, null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CLAVE_SERVICIO_INVALIDA"));
    }

    private ResultActions anonimizar(UUID id, String email) throws Exception {
        return mvc.perform(post("/clientes/{id}/anonimizacion", id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email))));
    }

    private ResultActions consultarSituacion(UUID id) throws Exception {
        return mvc.perform(get("/clientes/{id}/situacion-crediticia", id)
                .header("X-Api-Key", SituacionCrediticiaApiTest.CLAVE_CALIFICACION));
    }

    private void calificar(UUID cliente, String origen) {
        historial.registrar(new CalificacionHistorica(UUID.randomUUID(), cliente, "POTABLE", 35, origen,
                Instant.parse("2026-10-11T18:30:00Z"), null, List.of("motivo")), Instant.now());
    }

    private int calificaciones(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM historial_calificacion WHERE cliente_id = ?", Integer.class,
                id);
    }

    private int situacionesGuardadas(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM situacion_crediticia WHERE cliente_id = ?", Integer.class, id);
    }

    private UUID alta(String cuerpo) throws Exception {
        String respuesta = mvc.perform(post("/clientes").header("X-Api-Key", CLAVE)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(respuesta).get("clienteId").asText());
    }
}
