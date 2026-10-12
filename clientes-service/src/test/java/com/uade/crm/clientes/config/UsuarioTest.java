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

/** Design D9: {@code X-Usuario} queda en los logs del request. */
@PruebaWebMvc
@ExtendWith(OutputCaptureExtension.class)
class UsuarioTest {

    @Autowired
    MockMvc mvc;

    @Test
    void elUsuarioApareceEnLosLogsDelRequest(CapturedOutput salida) throws Exception {
        mvc.perform(get("/clientes/{id}", UUID.randomUUID()).header("X-Usuario", "tm-ana"));

        assertThat(salida.getOut()).contains("[tm-ana]").contains("Consulta de cliente");
    }

    @Test
    void unValorFueraDeFormatoSeIgnora(CapturedOutput salida) throws Exception {
        mvc.perform(get("/clientes/{id}", UUID.randomUUID()).header("X-Usuario", "ana\r\nFALSO log"));

        assertThat(salida.getOut()).contains("Consulta de cliente").doesNotContain("FALSO log").contains("[]");
    }
}
