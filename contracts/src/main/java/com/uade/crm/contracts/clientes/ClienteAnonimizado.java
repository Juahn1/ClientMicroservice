package com.uade.crm.contracts.clientes;

import java.util.Objects;
import java.util.UUID;

/**
 * El cliente fue anonimizado a pedido del titular (tópico {@code cliente-anonimizado}, clave {@code clienteId}).
 * Los consumidores tienen que dejar de usar los datos personales que hayan copiado.
 */
public record ClienteAnonimizado(UUID clienteId) {

    public static final String TIPO = "ClienteAnonimizado";
    public static final int VERSION = 1;

    public ClienteAnonimizado {
        Objects.requireNonNull(clienteId, "clienteId");
    }
}
