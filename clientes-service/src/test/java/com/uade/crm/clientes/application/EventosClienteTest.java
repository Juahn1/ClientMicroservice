package com.uade.crm.clientes.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.uade.crm.clientes.config.CorrelationIdFilter;
import com.uade.crm.clientes.domain.event.ClienteActualizadoEvento;
import com.uade.crm.contracts.clientes.CampoCliente;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** Design D8: los eventos toman el correlationId presente en el MDC al crearse. */
class EventosClienteTest {

    private static final Instant AHORA = Instant.parse("2026-10-08T12:00:00Z");

    @AfterEach
    void limpiarMdc() {
        MDC.clear();
    }

    @Test
    void capturaElCorrelationIdDelMdcAlCrearse() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "abc-123");
        UUID id = UUID.randomUUID();

        ClienteActualizadoEvento actualizado = EventosCliente.actualizado(id, Set.of(CampoCliente.NOMBRE), AHORA);
        var anonimizado = EventosCliente.anonimizado(id, AHORA);
        MDC.remove(CorrelationIdFilter.MDC_KEY);

        assertThat(actualizado.correlationId()).isEqualTo("abc-123");
        assertThat(anonimizado.correlationId()).isEqualTo("abc-123");
        assertThat(actualizado.clienteId()).isEqualTo(id);
    }

    @Test
    void sinRequestElCorrelationIdQuedaNulo() {
        assertThat(EventosCliente.anonimizado(UUID.randomUUID(), AHORA).correlationId()).isNull();
    }

    @Test
    void unEventoDeActualizacionNecesitaCampos() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ClienteActualizadoEvento(UUID.randomUUID(), Set.of(), AHORA, null));
    }
}
