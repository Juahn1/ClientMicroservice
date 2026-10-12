package com.uade.crm.clientes.domain.event;

import com.uade.crm.contracts.clientes.CampoCliente;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Evento de dominio: cambió al menos un campo de la ficha (design D8 del change 03). Lleva el {@code correlationId}
 * del request que lo originó, capturado al crearse, para no depender del hilo en que corra el listener.
 */
public record ClienteActualizadoEvento(UUID clienteId, Set<CampoCliente> campos, Instant ocurridoEn,
        String correlationId) {

    public ClienteActualizadoEvento {
        Objects.requireNonNull(clienteId, "clienteId");
        Objects.requireNonNull(ocurridoEn, "ocurridoEn");
        if (campos == null || campos.isEmpty()) {
            throw new IllegalArgumentException("Un ClienteActualizadoEvento necesita al menos un campo modificado");
        }
        campos = Set.copyOf(EnumSet.copyOf(campos));
    }
}
