package com.uade.crm.clientes.domain.port;

/**
 * Al guardar, otro cliente activo ya tenía el mismo email (índice único {@code ux_cliente_email_activo}). La lanza
 * el repositorio solo para esa restricción: el alta la usa para resolver la carrera de dos altas simultáneas y la
 * edición para responder 409. Cualquier otra violación de integridad sigue siendo un error inesperado.
 */
public class EmailDuplicadoException extends RuntimeException {

    public EmailDuplicadoException(Throwable causa) {
        super("Ya existe otro cliente activo con el mismo email", causa);
    }
}
