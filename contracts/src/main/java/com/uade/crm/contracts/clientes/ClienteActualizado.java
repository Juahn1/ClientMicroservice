package com.uade.crm.contracts.clientes;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cambió al menos un campo de la ficha (tópico {@code cliente-actualizado}, clave {@code clienteId}). No lleva los
 * valores nuevos: el consumidor que los necesite consulta {@code GET /clientes/{id}}.
 */
public record ClienteActualizado(UUID clienteId, List<CampoCliente> campos) {

    public static final String TIPO = "ClienteActualizado";
    public static final int VERSION = 1;

    public ClienteActualizado {
        Objects.requireNonNull(clienteId, "clienteId");
        campos = List.copyOf(campos);
    }
}
