package com.uade.crm.clientes.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uade.crm.contracts.EventoIntegracion;
import com.uade.crm.contracts.clientes.ClienteAnonimizado;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaTemplate;

/** Requirement: La publicación no altera la operación (a nivel del adaptador de Kafka). */
@ExtendWith(OutputCaptureExtension.class)
class PublicadorEventosKafkaTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
    private final PublicadorEventosKafka publicador = new PublicadorEventosKafka(kafka);

    @Test
    void unaFallaSincronicaDelProductorSeLoguea(CapturedOutput salida) {
        UUID id = UUID.randomUUID();
        when(kafka.send(anyString(), anyString(), any())).thenThrow(new TimeoutException("sin metadata"));

        assertThatNoException().isThrownBy(() -> publicador.publicar("cliente-anonimizado", id, evento(id)));

        assertThat(salida.getOut()).contains("Falló la publicación de ClienteAnonimizado del cliente " + id);
    }

    @Test
    void unEnvioQueFallaDespuesSeLoguea(CapturedOutput salida) {
        UUID id = UUID.randomUUID();
        when(kafka.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("delivery timeout")));

        publicador.publicar("cliente-anonimizado", id, evento(id));

        assertThat(salida.getOut()).contains("Falló la publicación de ClienteAnonimizado del cliente " + id);
    }

    private static EventoIntegracion<ClienteAnonimizado> evento(UUID id) {
        return new EventoIntegracion<>(UUID.randomUUID(), ClienteAnonimizado.TIPO, ClienteAnonimizado.VERSION,
                Instant.now(), null, new ClienteAnonimizado(id));
    }
}
