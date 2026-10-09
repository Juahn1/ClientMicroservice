package com.uade.crm.clientes.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de dominio: el cliente fue anonimizado (design D6 y D8 del change 03). Se publica dentro de la transacción,
 * así los módulos que guarden datos del cliente (CUIL, consentimientos, situación crediticia) los pueden borrar en
 * la misma transacción; el listener de integración lo envía a Kafka después del commit.
 */
public record ClienteAnonimizadoEvento(UUID clienteId, Instant ocurridoEn, String correlationId) {

    public ClienteAnonimizadoEvento {
        Objects.requireNonNull(clienteId, "clienteId");
        Objects.requireNonNull(ocurridoEn, "ocurridoEn");
    }
}
