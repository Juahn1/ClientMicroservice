package com.uade.crm.clientes.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uade.crm.pruebas.PruebaWebMvc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Requirement: Datos personales fuera de los logs. */
@PruebaWebMvc
@ExtendWith(OutputCaptureExtension.class)
class DatosPersonalesEnLogsTest {

    private static final String BODY = """
            { "nombre": "Jorge Paz", "cuil": "20-12345678-6", "telefono": "11 1234-5678" }
            """;

    @Autowired
    MockMvc mvc;

    @Test
    void elLogDeUnaOperacionNoContieneCuilNiTelefono(CapturedOutput salida) throws Exception {
        mvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        assertThat(salida.getOut()).contains("Alta recibida").contains("XX-XXXX5678-X");
        assertSinDatosPersonales(salida);
    }

    @Test
    void laTrazaDeUnaExcepcionTampocoLosContiene(CapturedOutput salida) throws Exception {
        mvc.perform(post("/clientes/falla").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isInternalServerError());

        assertThat(salida.getOut()).contains("IllegalStateException");
        assertSinDatosPersonales(salida);
    }

    private static void assertSinDatosPersonales(CapturedOutput salida) {
        assertThat(salida.getAll())
                .doesNotContain("20123456786")
                .doesNotContain("20-12345678-6")
                .doesNotContain("1112345678")
                .doesNotContain("11 1234-5678");
    }
}
