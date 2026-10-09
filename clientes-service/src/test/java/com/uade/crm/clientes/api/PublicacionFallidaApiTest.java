package com.uade.crm.clientes.api;

import static com.uade.crm.clientes.api.AltaClienteApiTest.CLAVE;
import static com.uade.crm.clientes.api.AltaClienteApiTest.martina;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uade.crm.clientes.PruebaIntegracion;
import com.uade.crm.clientes.domain.port.PublicadorEventos;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Requirement: La publicación no altera la operación (clientes-eventos), de punta a punta: con el publicador
 * fallando, la edición responde 200, queda guardada y el log registra la falla con el clienteId.
 */
@PruebaIntegracion
@ExtendWith(OutputCaptureExtension.class)
class PublicacionFallidaApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @MockitoBean
    PublicadorEventos publicador;

    @Test
    void conElBrokerNoDisponibleLaEdicionResponde200(CapturedOutput salida) throws Exception {
        doThrow(new IllegalStateException("broker no disponible")).when(publicador).publicar(any(), any(), any());
        String alta = mvc.perform(post("/clientes").header("X-Api-Key", CLAVE).contentType(MediaType.APPLICATION_JSON)
                        .content(martina("broker-caido@gmail.com", null, null)))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(alta).get("clienteId").asText());

        mvc.perform(patch("/clientes/{id}", id).header("If-Match", "W/\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"apellido\": \"Gómez\" }"))
                .andExpect(status().isOk());

        mvc.perform(get("/clientes/{id}", id)).andExpect(jsonPath("$.apellido").value("Gómez"));
        assertThat(salida.getOut()).contains("No se pudo publicar ClienteActualizado del cliente " + id);
    }
}
