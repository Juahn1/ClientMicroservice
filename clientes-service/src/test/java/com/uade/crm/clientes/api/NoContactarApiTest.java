package com.uade.crm.clientes.api;

import static com.uade.crm.clientes.api.AltaClienteApiTest.CLAVE;
import static com.uade.crm.clientes.api.AltaClienteApiTest.martina;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uade.crm.clientes.MensajesKafka;
import com.uade.crm.clientes.PruebaIntegracion;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Requirement: Marca no contactar (clientes-gestion), el recontacto que la quita, y los escenarios de clientes-eventos
 * sobre la marca.
 */
@PruebaIntegracion
@Import(MensajesKafka.class)
class NoContactarApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MensajesKafka mensajes;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM situacion_crediticia");
        jdbc.update("DELETE FROM consentimiento");
        jdbc.update("DELETE FROM historial_calificacion");
        jdbc.update("DELETE FROM cliente");
    }

    @Test
    void marcarComoNoContactar() throws Exception {
        UUID id = alta("martina@gmail.com");

        mvc.perform(put("/clientes/{id}/no-contactar", id)).andExpect(status().isNoContent());

        mvc.perform(get("/clientes/{id}", id))
                .andExpect(jsonPath("$.noContactar").value(true))
                .andExpect(jsonPath("$.noContactarDesde").isString())
                .andExpect(jsonPath("$.email").value("martina@gmail.com"))
                .andExpect(jsonPath("$.contactosRegistrados").value(1));
        mvc.perform(get("/clientes").param("email", "martina@gmail.com"))
                .andExpect(jsonPath("$.contenido[0].id").value(id.toString()))
                .andExpect(jsonPath("$.contenido[0].noContactar").value(true));

        JsonNode sobre = json.readTree(mensajes.esperar("cliente-actualizado", id, 1).getFirst().value());
        assertThat(sobre.get("payload").get("campos")).extracting(JsonNode::asText).containsExactly("NO_CONTACTAR");
    }

    @Test
    void marcarDosVecesConservaLaFechaYPublicaUnaSolaVez() throws Exception {
        UUID id = alta("martina@gmail.com");
        mvc.perform(put("/clientes/{id}/no-contactar", id)).andExpect(status().isNoContent());
        String desde = fecha(id);

        mvc.perform(put("/clientes/{id}/no-contactar", id)).andExpect(status().isNoContent());

        assertThat(fecha(id)).isEqualTo(desde);
        assertThat(mensajes.ninguno("cliente-actualizado", id)).hasSize(1);
    }

    @Test
    void quitarLaMarcaPublicaElEvento() throws Exception {
        UUID id = alta("martina@gmail.com");
        mvc.perform(put("/clientes/{id}/no-contactar", id)).andExpect(status().isNoContent());

        mvc.perform(delete("/clientes/{id}/no-contactar", id)).andExpect(status().isNoContent());

        mvc.perform(get("/clientes/{id}", id))
                .andExpect(jsonPath("$.noContactar").value(false))
                .andExpect(jsonPath("$.noContactarDesde").value(nullValue()));
        assertThat(mensajes.esperar("cliente-actualizado", id, 2)).hasSize(2);
    }

    @Test
    void quitarUnaMarcaInexistenteNoCambiaNiPublica() throws Exception {
        UUID id = alta("martina@gmail.com");

        mvc.perform(delete("/clientes/{id}/no-contactar", id)).andExpect(status().isNoContent());

        mvc.perform(get("/clientes/{id}", id)).andExpect(header().string("ETag", "W/\"0\""));
        assertThat(mensajes.ninguno("cliente-actualizado", id)).isEmpty();
    }

    @Test
    void marcaSobreUnClienteAnonimizado() throws Exception {
        UUID id = alta("martina@gmail.com");
        jdbc.update("UPDATE cliente SET estado = 'ANONIMIZADO', email = NULL WHERE id = ?", id);

        mvc.perform(put("/clientes/{id}/no-contactar", id))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("CLIENTE_ANONIMIZADO"));
        mvc.perform(delete("/clientes/{id}/no-contactar", id)).andExpect(status().isGone());
    }

    @Test
    void marcaSobreUnClienteInexistente() throws Exception {
        mvc.perform(put("/clientes/{id}/no-contactar", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CLIENTE_NO_ENCONTRADO"));
    }

    /** Scenarios: Recontacto de un cliente marcado como no contactar (gestion) y Recontacto (eventos). */
    @Test
    void elRecontactoQuitaLaMarcaSinPublicar() throws Exception {
        UUID id = alta("martina@gmail.com");
        mvc.perform(put("/clientes/{id}/no-contactar", id)).andExpect(status().isNoContent());

        mvc.perform(post("/clientes").header("X-Api-Key", CLAVE).contentType(MediaType.APPLICATION_JSON)
                        .content(martina("martina@gmail.com", null, "WHATSAPP")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(id.toString()));

        mvc.perform(get("/clientes/{id}", id))
                .andExpect(jsonPath("$.noContactar").value(false))
                .andExpect(jsonPath("$.noContactarDesde").value(nullValue()))
                .andExpect(jsonPath("$.contactosRegistrados").value(2));
        assertThat(mensajes.ninguno("cliente-actualizado", id)).hasSize(1);
    }

    private String fecha(UUID id) throws Exception {
        String ficha = mvc.perform(get("/clientes/{id}", id)).andReturn().getResponse().getContentAsString();
        return json.readTree(ficha).get("noContactarDesde").asText();
    }

    private UUID alta(String email) throws Exception {
        String respuesta = mvc.perform(post("/clientes").header("X-Api-Key", CLAVE)
                        .contentType(MediaType.APPLICATION_JSON).content(martina(email, null, null)))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(respuesta).get("clienteId").asText());
    }
}
