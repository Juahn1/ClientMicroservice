package com.uade.crm.clientes.application;

import java.util.UUID;

/**
 * El email de confirmación de la anonimización no es el del cliente (design D6). El mensaje no revela el email
 * registrado.
 */
public class ConfirmacionInvalidaException extends RuntimeException {

    public ConfirmacionInvalidaException(UUID clienteId) {
        super("El email no coincide con el del cliente " + clienteId + "; no se anonimizó");
    }
}
