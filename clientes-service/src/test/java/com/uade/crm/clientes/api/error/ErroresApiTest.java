package com.uade.crm.clientes.api.error;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uade.crm.pruebas.PruebaWebMvc;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Requirement: Formato de errores. */
@PruebaWebMvc
class ErroresApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void recursoInexistenteResponde404ConCodigoYCorrelationId() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(get("/clientes/{id}", id).header("X-Correlation-Id", "abc-123"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("CLIENTE_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.correlationId").value("abc-123"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.type").value("https://crm.uade.local/errores/cliente-no-encontrado"))
                .andExpect(jsonPath("$.instance").value("/clientes/" + id));
    }

    @Test
    void datosInvalidosResponde422ConListaDeErrores() throws Exception {
        mvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nombre": "M", "email": "no-es-un-email" }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.correlationId", notNullValue()))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("nombre")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("email")))
                .andExpect(jsonPath("$.errores[0].mensaje", notNullValue()));
    }

    @Test
    void cuerpoMalFormadoResponde400() throws Exception {
        mvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON).content("{ \"nombre\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"))
                .andExpect(jsonPath("$.correlationId", notNullValue()));
    }

    @Test
    void erroresDeSpringMvcTambienLlevanCodigo() throws Exception {
        mvc.perform(get("/clientes"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.correlationId", notNullValue()));
    }

    @Test
    void errorInesperadoResponde500ConCodigo() throws Exception {
        mvc.perform(post("/clientes/falla").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nombre\": \"Martina\" }"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"));
    }
}
