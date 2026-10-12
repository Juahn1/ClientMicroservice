package com.uade.crm.clientes.api.seguridad;

/** Falta {@code X-Api-Key} o no coincide. El mensaje nunca incluye la clave recibida. */
public class ClaveServicioInvalidaException extends RuntimeException {

    public ClaveServicioInvalidaException() {
        super("Este endpoint requiere una clave de servicio válida en el header " + ClaveServicioInterceptor.HEADER);
    }
}
