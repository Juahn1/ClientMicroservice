package com.uade.crm.clientes.api.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uade.crm.pruebas.PruebaWebMvc;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Requirement: Acceso restringido a servicios autorizados (mecanismo, sobre un endpoint de prueba). */
@PruebaWebMvc
@ExtendWith(OutputCaptureExtension.class)
class ClaveServicioTest {

    /** Las de application-test.yml. */
    private static final String CLAVE = "solo-test-clave-de-servicio-leads-0001";
    private static final String CLAVE_CALIFICACION = "solo-test-clave-de-servicio-calificacion-0001";
    private static final String VALIDO = "{ \"nombre\": \"Martina\" }";

    @Autowired
    MockMvc mvc;

    @Test
    void sinClaveResponde401ConCodigo() throws Exception {
        mvc.perform(post("/pruebas/interno").contentType(MediaType.APPLICATION_JSON).content(VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("CLAVE_SERVICIO_INVALIDA"))
                .andExpect(header().string("WWW-Authenticate", "ApiKey realm=\"clientes\", header=\"X-Api-Key\""));
    }

    @Test
    void conClaveIncorrectaYCuerpoInvalidoResponde401YNo422() throws Exception {
        mvc.perform(post("/pruebas/interno").header("X-Api-Key", CLAVE + "x")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nombre\": \"M\" }"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CLAVE_SERVICIO_INVALIDA"));
    }

    @Test
    void conLaClaveDeLeadsSeProcesa() throws Exception {
        mvc.perform(post("/pruebas/interno").header("X-Api-Key", CLAVE)
                        .contentType(MediaType.APPLICATION_JSON).content(VALIDO))
                .andExpect(status().isOk());
    }

    @Test
    void conLaClaveElCuerpoSeValidaNormalmente() throws Exception {
        mvc.perform(post("/pruebas/interno").header("X-Api-Key", CLAVE)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"nombre\": \"M\" }"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void laClaveDeCalificacionNoAbreUnEndpointDeLeads() throws Exception {
        mvc.perform(post("/pruebas/interno").header("X-Api-Key", CLAVE_CALIFICACION)
                        .contentType(MediaType.APPLICATION_JSON).content(VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CLAVE_SERVICIO_INVALIDA"));
    }

    @Test
    void conLaClaveDeCalificacionSeProcesaSuEndpoint() throws Exception {
        mvc.perform(get("/pruebas/calificacion").header("X-Api-Key", CLAVE_CALIFICACION))
                .andExpect(status().isOk());
    }

    @Test
    void laClaveDeLeadsNoAbreUnEndpointDeCalificacion() throws Exception {
        mvc.perform(get("/pruebas/calificacion").header("X-Api-Key", CLAVE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CLAVE_SERVICIO_INVALIDA"));
        mvc.perform(get("/pruebas/calificacion"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void laClaveDeCalificacionNoApareceEnLosLogs(CapturedOutput salida) throws Exception {
        mvc.perform(get("/pruebas/calificacion").header("X-Api-Key", CLAVE_CALIFICACION));

        assertThat(salida.getAll()).doesNotContain(CLAVE_CALIFICACION);
    }

    @Test
    void losEndpointsSinAnotacionNoPidenClave() throws Exception {
        mvc.perform(get("/clientes/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void laClaveNoApareceEnLosLogs(CapturedOutput salida) throws Exception {
        mvc.perform(post("/pruebas/interno").header("X-Api-Key", CLAVE)
                .contentType(MediaType.APPLICATION_JSON).content(VALIDO));
        mvc.perform(post("/pruebas/interno").header("X-Api-Key", "otra-clave-que-no-coincide-con-nada")
                .contentType(MediaType.APPLICATION_JSON).content(VALIDO));

        assertThat(salida.getOut()).contains("Alta interna recibida");
        assertThat(salida.getAll()).doesNotContain(CLAVE).doesNotContain("otra-clave-que-no-coincide-con-nada");
    }
}
