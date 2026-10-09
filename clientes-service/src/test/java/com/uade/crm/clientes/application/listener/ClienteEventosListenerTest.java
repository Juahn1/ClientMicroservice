package com.uade.crm.clientes.application.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uade.crm.clientes.config.ClientesProperties;
import com.uade.crm.clientes.domain.event.ClienteActualizadoEvento;
import com.uade.crm.clientes.domain.event.ClienteAnonimizadoEvento;
import com.uade.crm.clientes.domain.port.PublicadorEventos;
import com.uade.crm.contracts.clientes.CampoCliente;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Requirement: La publicación no altera la operación (a nivel del listener). */
@ExtendWith(OutputCaptureExtension.class)
class ClienteEventosListenerTest {

    private static final Instant AHORA = Instant.parse("2026-10-08T12:00:00Z");

    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private ClienteEventosListener listener;

    @BeforeEach
    void crear() {
        ClientesProperties properties = mock(ClientesProperties.class);
        when(properties.kafka()).thenReturn(new ClientesProperties.Kafka(
                new ClientesProperties.Kafka.Topicos("cliente-actualizado", "cliente-anonimizado"), 3, 1));
        listener = new ClienteEventosListener(publicador, properties);
    }

    @Test
    void unaFallaAlPublicarNoSePropagaYSeLogueaConElClienteId(CapturedOutput salida) {
        UUID id = UUID.randomUUID();
        doThrow(new IllegalStateException("broker caído"))
                .when(publicador).publicar(eq("cliente-actualizado"), eq(id), any());

        assertThatNoException().isThrownBy(() -> listener.clienteActualizado(
                new ClienteActualizadoEvento(id, Set.of(CampoCliente.NOMBRE), AHORA, "abc-123")));

        assertThat(salida.getOut()).contains("No se pudo publicar ClienteActualizado del cliente " + id);
    }

    @Test
    void tambienParaLaAnonimizacion(CapturedOutput salida) {
        UUID id = UUID.randomUUID();
        doThrow(new IllegalStateException("broker caído"))
                .when(publicador).publicar(eq("cliente-anonimizado"), eq(id), any());

        assertThatNoException().isThrownBy(() -> listener.clienteAnonimizado(
                new ClienteAnonimizadoEvento(id, AHORA, null)));

        assertThat(salida.getOut()).contains("No se pudo publicar ClienteAnonimizado del cliente " + id);
    }
}
