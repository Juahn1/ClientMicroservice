package com.uade.crm.clientes.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.uade.crm.pruebas.PruebaWebMvc;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;

/** Requirement: Propagación del correlationId. */
@PruebaWebMvc
@ExtendWith(OutputCaptureExtension.class)
class CorrelationIdTest {

    @Autowired
    MockMvc mvc;

    @Test
    void headerPresenteSeDevuelveYApareceEnLosLogs(CapturedOutput salida) throws Exception {
        String header = mvc.perform(get("/clientes/{id}", UUID.randomUUID()).header("X-Correlation-Id", "abc-123"))
                .andReturn().getResponse().getHeader("X-Correlation-Id");

        assertThat(header).isEqualTo("abc-123");
        assertThat(salida.getOut()).contains("[abc-123]").contains("Consulta de cliente");
    }

    @Test
    void headerAusenteGeneraUnUuid() throws Exception {
        String header = mvc.perform(get("/clientes/{id}", UUID.randomUUID()))
                .andReturn().getResponse().getHeader("X-Correlation-Id");

        assertThat(header).isNotBlank();
        assertThat(UUID.fromString(header)).isNotNull();
    }

    @Test
    void headerConFormatoInvalidoSeReemplazaPorUnUuid() throws Exception {
        String header = mvc.perform(get("/clientes/{id}", UUID.randomUUID())
                        .header("X-Correlation-Id", "abc\r\nFALSO log"))
                .andReturn().getResponse().getHeader("X-Correlation-Id");

        assertThat(UUID.fromString(header)).isNotNull();
    }
}
