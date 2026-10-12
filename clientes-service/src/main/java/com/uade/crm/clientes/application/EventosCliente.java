package com.uade.crm.clientes.application;

import com.uade.crm.clientes.config.CorrelationIdFilter;
import com.uade.crm.clientes.domain.event.ClienteActualizadoEvento;
import com.uade.crm.clientes.domain.event.ClienteAnonimizadoEvento;
import com.uade.crm.contracts.clientes.CampoCliente;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;

/** Crea los eventos de dominio con el {@code correlationId} del request en curso (design D8 del change 03). */
final class EventosCliente {

    private EventosCliente() {
    }

    static ClienteActualizadoEvento actualizado(UUID clienteId, Set<CampoCliente> campos, Instant ahora) {
        return new ClienteActualizadoEvento(clienteId, campos, ahora, correlationId());
    }

    static ClienteAnonimizadoEvento anonimizado(UUID clienteId, Instant ahora) {
        return new ClienteAnonimizadoEvento(clienteId, ahora, correlationId());
    }

    private static String correlationId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
