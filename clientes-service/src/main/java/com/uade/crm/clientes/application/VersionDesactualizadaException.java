package com.uade.crm.clientes.application;

import java.util.UUID;

/** El {@code If-Match} no coincide con la versión vigente, u otra escritura ganó la carrera (design D4). */
public class VersionDesactualizadaException extends RuntimeException {

    public VersionDesactualizadaException(UUID clienteId) {
        super("El cliente " + clienteId + " cambió desde que se leyó; volvé a consultar la ficha");
    }
}
