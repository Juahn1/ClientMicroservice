package com.uade.crm.clientes.domain.model;

import java.util.UUID;

public class ClienteNoEncontradoException extends RuntimeException {

    private final UUID clienteId;

    public ClienteNoEncontradoException(UUID clienteId) {
        super("No existe el cliente " + clienteId);
        this.clienteId = clienteId;
    }

    public UUID clienteId() {
        return clienteId;
    }
}
