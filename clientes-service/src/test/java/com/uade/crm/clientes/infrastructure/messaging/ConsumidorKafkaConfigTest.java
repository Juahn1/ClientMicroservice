package com.uade.crm.clientes.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.uade.crm.clientes.config.ClientesProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.util.backoff.BackOffExecution;

/** Esperas entre reintentos de una falla transitoria (design D5 del change 08). */
class ConsumidorKafkaConfigTest {

    @Test
    void laEsperaCreceSinElTopePorDefectoYSeCortaDespuesDeLosIntentos() {
        BackOffExecution ejecucion = ConsumidorKafkaConfig.espera(
                new ClientesProperties.Historial.Reintentos(5, Duration.ofSeconds(2), 2)).start();

        List<Long> esperas = new ArrayList<>();
        for (long espera = ejecucion.nextBackOff(); espera != BackOffExecution.STOP; espera = ejecucion.nextBackOff()) {
            esperas.add(espera);
        }

        assertThat(esperas).containsExactly(2_000L, 4_000L, 8_000L, 16_000L, 32_000L);
    }

    @Test
    void laCausaNombraElCampoInvalidoOSoloElTipoDeLaFalla() {
        RuntimeException invalido = new RuntimeException("envoltorio con datos 123",
                new MensajeInvalidoException("clienteId", "no es un UUID"));
        RuntimeException deBase = new RuntimeException("envoltorio",
                new IllegalStateException("insert into historial values ('dato')"));

        assertThat(ConsumidorKafkaConfig.causa(invalido)).contains("'clienteId' no es un UUID")
                .doesNotContain("123");
        assertThat(ConsumidorKafkaConfig.causa(deBase)).isEqualTo("IllegalStateException");
    }

    @Test
    void sinIntentosNoReintenta() {
        BackOffExecution ejecucion = ConsumidorKafkaConfig.espera(
                new ClientesProperties.Historial.Reintentos(0, Duration.ofMillis(50), 2)).start();

        assertThat(ejecucion.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }
}
