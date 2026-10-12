package com.uade.crm.clientes.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uade.crm.clientes.PruebaIntegracion;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.port.ClienteRepositorio;
import com.uade.crm.clientes.domain.valor.Telefono;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Requirements de {@code clientes-gestion}: Alta de cliente, Deduplicación por email, El recontacto no sobrescribe
 * datos existentes, Normalización del teléfono y Validación de los datos de alta. De {@code clientes-transversal}:
 * Acceso restringido a servicios autorizados sobre el endpoint real. Las altas simultáneas se prueban en
 * {@code AltaClienteServiceTest}.
 */
@PruebaIntegracion
@ExtendWith(OutputCaptureExtension.class)
class AltaClienteApiTest {

    static final String CLAVE = "solo-test-clave-de-servicio-leads-0001";

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

    // --- Alta de cliente

    @Test
    void clienteNuevo() throws Exception {
        ResultActions respuesta = alta(martina("martina@gmail.com", "11 1234-5678", "INSTAGRAM"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nuevo").value(true))
                .andExpect(jsonPath("$.coincidencia").value("NINGUNA"));

        UUID id = idDe(respuesta);
        respuesta.andExpect(header().string("Location", "/clientes/" + id));
        Cliente cliente = repositorio.buscarActivoPorId(id).orElseThrow();
        assertThat(cliente.contactosRegistrados()).isEqualTo(1);
        assertThat(cliente.ultimoCanal()).contains(CanalOrigen.INSTAGRAM);
        assertThat(cliente.canalOrigen()).contains(CanalOrigen.INSTAGRAM);
    }

    @Test
    void clienteNuevoSinTelefonoNiCanal() throws Exception {
        UUID id = idDe(alta(martina("martina@gmail.com", null, null)).andExpect(status().isCreated()));

        Cliente cliente = repositorio.buscarActivoPorId(id).orElseThrow();
        assertThat(cliente.telefono()).isEmpty();
        assertThat(cliente.canalOrigen()).isEmpty();
        assertThat(cliente.ultimoCanal()).isEmpty();
    }

    // --- Deduplicación por email

    @Test
    void mismoEmailConOtroFormato() throws Exception {
        UUID id = idDe(alta(martina("martina@gmail.com", null, null)));

        alta(martina(" Martina@Gmail.com ", null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(id.toString()))
                .andExpect(jsonPath("$.nuevo").value(false))
                .andExpect(jsonPath("$.coincidencia").value("EMAIL"))
                .andExpect(header().string("Location", "/clientes/" + id));
    }

    // --- El recontacto no sobrescribe datos existentes

    @Test
    void recontactoConDatosDistintos() throws Exception {
        UUID id = idDe(alta(martina("martina@gmail.com", "+5491112345678", "INSTAGRAM")));

        alta("""
                { "nombre": "Martu", "apellido": "Gómez", "email": "martina@gmail.com", "provincia": "CORDOBA",
                  "localidad": "Lanús", "telefono": "351 412-3456", "canalOrigen": "WHATSAPP" }
                """).andExpect(status().isOk());

        Cliente cliente = repositorio.buscarActivoPorId(id).orElseThrow();
        assertThat(cliente.nombre()).isEqualTo("Martina");
        assertThat(cliente.apellido()).isEqualTo("López");
        assertThat(cliente.provincia()).isEqualTo(Provincia.BUENOS_AIRES);
        assertThat(cliente.telefono()).contains(Telefono.deE164("+5491112345678"));
        assertThat(cliente.localidad()).contains("Lanús");
        assertThat(cliente.telefonoAlternativo()).contains(Telefono.deE164("+5493514123456"));
        assertThat(cliente.canalOrigen()).contains(CanalOrigen.INSTAGRAM);
        assertThat(cliente.ultimoCanal()).contains(CanalOrigen.WHATSAPP);
        assertThat(cliente.contactosRegistrados()).isEqualTo(2);
    }

    @Test
    void recontactoQueCompletaElTelefono() throws Exception {
        UUID id = idDe(alta(martina("martina@gmail.com", null, null)));

        alta(martina("martina@gmail.com", "11 1234-5678", null)).andExpect(status().isOk());

        Cliente cliente = repositorio.buscarActivoPorId(id).orElseThrow();
        assertThat(cliente.telefono()).contains(Telefono.deE164("+5491112345678"));
        assertThat(cliente.telefonoAlternativo()).isEmpty();
    }

    // --- Normalización del teléfono

    @Test
    void numeroSinElNueveDeMovil() throws Exception {
        UUID id = idDe(alta(martina("a@gmail.com", "11 1234-5678", null)));

        assertThat(repositorio.buscarActivoPorId(id).orElseThrow().telefono())
                .contains(Telefono.deE164("+5491112345678"));
    }

    @Test
    void numeroConCeroYQuince() throws Exception {
        UUID id = idDe(alta(martina("b@gmail.com", "0351 15 412-3456", null)));

        assertThat(repositorio.buscarActivoPorId(id).orElseThrow().telefono())
                .contains(Telefono.deE164("+5493514123456"));
    }

    @Test
    void numeroIncompleto() throws Exception {
        alta(martina("c@gmail.com", "1512345678", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TELEFONO_INVALIDO"))
                .andExpect(jsonPath("$.errores[0].campo").value("telefono"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cliente", Integer.class)).isZero();
    }

    // --- Validación de los datos de alta

    @Test
    void provinciaInexistente() throws Exception {
        alta("""
                { "nombre": "Martina", "apellido": "López", "email": "m@gmail.com", "provincia": "BS AS" }
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("provincia")));
    }

    @Test
    void emailAusente() throws Exception {
        alta("""
                { "nombre": "Martina", "apellido": "López", "provincia": "CORDOBA" }
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("email")));
    }

    @Test
    void apellidoConCaracteresInvalidos() throws Exception {
        alta("""
                { "nombre": "Martina", "apellido": "L0pez", "email": "m@gmail.com", "provincia": "CORDOBA" }
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("apellido")));
    }

    @Test
    void aceptaNombresCompuestos() throws Exception {
        alta("""
                { "nombre": "María José", "apellido": "D'Angelo Pérez-Gómez", "email": "mj@gmail.com",
                  "provincia": "CABA" }
                """)
                .andExpect(status().isCreated());
    }

    @Test
    void rechazaEmailSinDominioYNombreDeUnaLetra() throws Exception {
        alta("""
                { "nombre": " M ", "apellido": "López", "email": "m@gmail", "provincia": "CORDOBA" }
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("nombre")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("email")));
    }

    // --- Acceso restringido a servicios autorizados (endpoint real)

    @Test
    void altaSinClave() throws Exception {
        mvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content(martina("martina@gmail.com", null, null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CLAVE_SERVICIO_INVALIDA"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cliente", Integer.class)).isZero();
    }

    @Test
    void altaConClaveIncorrectaYCuerpoInvalido() throws Exception {
        mvc.perform(post("/clientes").header("X-Api-Key", "una-clave-incorrecta-de-mas-de-32-caracteres")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nombre\": \"Martina\", \"apellido\": \"López\", \"provincia\": \"CORDOBA\" }"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CLAVE_SERVICIO_INVALIDA"));
    }

    @Test
    void altaConLaClaveDeCalificacion() throws Exception {
        mvc.perform(post("/clientes").header("X-Api-Key", "solo-test-clave-de-servicio-calificacion-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(martina("martina@gmail.com", null, null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CLAVE_SERVICIO_INVALIDA"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cliente", Integer.class)).isZero();
    }

    @Test
    void laClaveYLosDatosPersonalesNoAparecenEnLosLogs(CapturedOutput salida) throws Exception {
        alta(martina("martina@gmail.com", "11 1234-5678", null)).andExpect(status().isCreated());

        assertThat(salida.getOut()).contains("Alta del cliente");
        assertThat(salida.getAll()).doesNotContain(CLAVE)
                .doesNotContain("martina@gmail.com")
                .doesNotContain("1112345678");
    }

    // --- OpenAPI

    @Test
    void elContratoDocumentaElAltaYSuSeguridad() throws Exception {
        String docs = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode contrato = json.readTree(docs);
        assertThat(contrato.at("/components/securitySchemes/claveServicio/name").asText()).isEqualTo("X-Api-Key");
        assertThat(contrato.at("/paths/~1clientes/post/security/0").has("claveServicio")).isTrue();
    }

    private ResultActions alta(String cuerpo) throws Exception {
        return mvc.perform(post("/clientes").header("X-Api-Key", CLAVE)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private UUID idDe(ResultActions resultado) throws Exception {
        return UUID.fromString(json.readTree(resultado.andReturn().getResponse().getContentAsString())
                .get("clienteId").asText());
    }

    static String martina(String email, String telefono, String canal) {
        return """
                { "nombre": "Martina", "apellido": "López", "email": %s, "telefono": %s,
                  "provincia": "BUENOS_AIRES", "canalOrigen": %s }
                """.formatted(texto(email), texto(telefono), texto(canal));
    }

    private static String texto(String valor) {
        return valor == null ? "null" : "\"" + valor + "\"";
    }
}
