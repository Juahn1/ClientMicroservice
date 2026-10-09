package com.uade.crm.clientes.domain.model;

import java.util.UUID;

/** El cliente existió y fue anonimizado: la API responde 410 (design D7). */
public class ClienteAnonimizadoException extends RuntimeException {

    private final UUID clienteId;

    public ClienteAnonimizadoException(UUID clienteId) {
        super("El cliente " + clienteId + " fue anonimizado");
        this.clienteId = clienteId;
    }

    public UUID clienteId() {
        return clienteId;
    }
}
