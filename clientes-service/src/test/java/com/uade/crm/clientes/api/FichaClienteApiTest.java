package com.uade.crm.clientes.api;

import static com.uade.crm.clientes.api.AltaClienteApiTest.CLAVE;
import static com.uade.crm.clientes.api.AltaClienteApiTest.martina;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uade.crm.clientes.PruebaIntegracion;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Requirement: Consulta de la ficha del cliente (y "Consultas sin clave" de clientes-transversal). */
@PruebaIntegracion
class FichaClienteApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM situacion_crediticia");
        jdbc.update("DELETE FROM consentimiento");
        jdbc.update("DELETE FROM historial_calificacion");
        jdbc.update("DELETE FROM cliente");
    }

    @Test
    void clienteExistenteSinClave() throws Exception {
        UUID id = alta(martina("martina@gmail.com", "11 1234-5678", "INSTAGRAM"));

        mvc.perform(get("/clientes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "W/\"0\""))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.nombre").value("Martina"))
                .andExpect(jsonPath("$.apellido").value("López"))
                .andExpect(jsonPath("$.email").value("martina@gmail.com"))
                .andExpect(jsonPath("$.telefono").value("+5491112345678"))
                .andExpect(jsonPath("$.telefonoAlternativo").value(nullValue()))
                .andExpect(jsonPath("$.provincia").value("BUENOS_AIRES"))
                .andExpect(jsonPath("$.localidad").value(nullValue()))
                .andExpect(jsonPath("$.canalOrigen").value("INSTAGRAM"))
                .andExpect(jsonPath("$.ultimoCanal").value("INSTAGRAM"))
                .andExpect(jsonPath("$.contactosRegistrados").value(1))
                .andExpect(jsonPath("$.ultimoContactoEn").isString())
                .andExpect(jsonPath("$.noContactar").value(false))
                .andExpect(jsonPath("$.noContactarDesde").value(nullValue()))
                .andExpect(jsonPath("$.cuil").value(nullValue()))
                .andExpect(jsonPath("$.consentimientosVigentes").isEmpty())
                .andExpect(jsonPath("$.requiereRevision").value(false))
                .andExpect(jsonPath("$.motivoRevision").value(nullValue()));
    }

    /** Escenarios "CUIL en la ficha" y "Consentimiento en la ficha" de clientes-privacidad. */
    @Test
    void cuilEnmascaradoYConsentimientoEnLaFicha() throws Exception {
        UUID id = alta(AltaConCuilApiTest.cuerpo("martina@gmail.com", null, "20-12345678-6", true));

        String ficha = mvc.perform(get("/clientes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuil").value("XX-XXXX5678-X"))
                .andExpect(jsonPath("$.consentimientosVigentes.length()").value(1))
                .andExpect(jsonPath("$.consentimientosVigentes[0].finalidad").value("PRECALIFICACION_CREDITICIA"))
                .andExpect(jsonPath("$.consentimientosVigentes[0].versionTexto").value("v1"))
                .andExpect(jsonPath("$.consentimientosVigentes[0].otorgadoEn").isString())
                .andReturn().getResponse().getContentAsString();
        assertThat(ficha).doesNotContain("20123456786", "12345678");
    }

    /** Escenario "Cliente sin revisión pendiente" y forma de una revisión pendiente. */
    @Test
    void motivoDeRevisionEnLaFicha() throws Exception {
        UUID id = alta(AltaConCuilApiTest.cuerpo("martina@gmail.com", null, "27-30111222-5", true));
        alta(AltaConCuilApiTest.cuerpo("martina@gmail.com", null, "20-12345678-6", true));

        mvc.perform(get("/clientes/{id}", id))
                .andExpect(jsonPath("$.cuil").value("XX-XXXX1222-X"))
                .andExpect(jsonPath("$.requiereRevision").value(true))
                .andExpect(jsonPath("$.motivoRevision").value("CUIL_DISTINTO"));
    }

    @Test
    void sinTelefonoNiCanalLosDevuelveEnNull() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));

        mvc.perform(get("/clientes/{id}", id))
                .andExpect(jsonPath("$.telefono").value(nullValue()))
                .andExpect(jsonPath("$.canalOrigen").value(nullValue()))
                .andExpect(jsonPath("$.ultimoCanal").value(nullValue()));
    }

    @Test
    void laVersionCambiaConUnRecontacto() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));
        String antes = mvc.perform(get("/clientes/{id}", id)).andReturn().getResponse().getHeader("ETag");

        alta(martina("martina@gmail.com", null, "WHATSAPP"));

        String despues = mvc.perform(get("/clientes/{id}", id)).andReturn().getResponse().getHeader("ETag");
        assertThat(antes).isEqualTo("W/\"0\"");
        assertThat(despues).isNotEqualTo(antes);
    }

    @Test
    void clienteInexistente() throws Exception {
        mvc.perform(get("/clientes/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CLIENTE_NO_ENCONTRADO"));
    }

    /** Scenario: Cliente anonimizado (change 03). */
    @Test
    void clienteAnonimizadoResponde410SinDatos() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));
        jdbc.update("UPDATE cliente SET estado = 'ANONIMIZADO', email = NULL, nombre = 'ANONIMIZADO', "
                + "apellido = 'ANONIMIZADO', anonimizado_en = now() WHERE id = ?", id);

        mvc.perform(get("/clientes/{id}", id))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("CLIENTE_ANONIMIZADO"))
                .andExpect(jsonPath("$.nombre").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void laFichaIncluyeLaMarcaNoContactar() throws Exception {
        UUID id = alta(martina("martina@gmail.com", null, null));

        mvc.perform(get("/clientes/{id}", id))
                .andExpect(jsonPath("$.noContactar").value(false))
                .andExpect(jsonPath("$.noContactarDesde").value(nullValue()));

        jdbc.update("UPDATE cliente SET no_contactar = TRUE, no_contactar_desde = now() WHERE id = ?", id);

        mvc.perform(get("/clientes/{id}", id))
                .andExpect(jsonPath("$.noContactar").value(true))
                .andExpect(jsonPath("$.noContactarDesde").isString());
    }

    private UUID alta(String cuerpo) throws Exception {
        String respuesta = mvc.perform(post("/clientes").header("X-Api-Key", CLAVE)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(respuesta).get("clienteId").asText());
    }
}
