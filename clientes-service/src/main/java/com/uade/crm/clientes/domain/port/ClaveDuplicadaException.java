package com.uade.crm.clientes.domain.port;

/**
 * Al guardar, otro cliente activo ya tenía el mismo email ({@code ux_cliente_email_activo}) o el mismo CUIL
 * ({@code ux_cliente_cuil_activo}). La lanza el repositorio solo para esas dos restricciones: el alta la usa para
 * resolver la carrera de dos altas simultáneas y la edición, si es por email, para responder 409. Cualquier otra
 * violación de integridad sigue siendo un error inesperado (design D5 del change 04).
 */
public class ClaveDuplicadaException extends RuntimeException {

    /** Qué clave única de cliente se violó. */
    public enum Clave {
        EMAIL, CUIL
    }

    private final Clave clave;

    public ClaveDuplicadaException(Clave clave, Throwable causa) {
        super("Ya existe otro cliente activo con el mismo " + clave, causa);
        this.clave = clave;
    }

    public Clave clave() {
        return clave;
    }
}
