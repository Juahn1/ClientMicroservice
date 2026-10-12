package com.uade.crm.clientes.domain.port;

import com.uade.crm.clientes.domain.valor.Cuil;
import java.util.Objects;
import java.util.UUID;

/**
 * Pedido a {@link SituacionCrediticiaPort} (design D2 del change 05). La fuente solo usa el CUIL; el
 * {@code clienteId} lo necesita la caché, cuya clave es el cliente. El CUIL viaja en claro solo en memoria.
 */
public record ConsultaSituacion(UUID clienteId, Cuil cuil) {

    public ConsultaSituacion {
        Objects.requireNonNull(clienteId, "clienteId");
        Objects.requireNonNull(cuil, "cuil");
    }

    /** Sin el CUIL, ni siquiera enmascarado: el {@code clienteId} alcanza para seguir la consulta en los logs. */
    @Override
    public String toString() {
        return "ConsultaSituacion[clienteId=" + clienteId + "]";
    }
}
