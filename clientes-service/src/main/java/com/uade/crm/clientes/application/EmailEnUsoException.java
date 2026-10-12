package com.uade.crm.clientes.application;

import java.util.UUID;

/** La edición usa el email de otro cliente activo (design D4). El mensaje no repite el email. */
public class EmailEnUsoException extends RuntimeException {

    public EmailEnUsoException(UUID clienteId) {
        super("El email informado ya pertenece a otro cliente; no se modificó el cliente " + clienteId);
    }
}
