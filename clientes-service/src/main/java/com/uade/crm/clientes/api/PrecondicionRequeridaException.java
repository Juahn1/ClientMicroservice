package com.uade.crm.clientes.api;

/** Una edición llegó sin {@code If-Match}: se rechaza antes de leer la base (design D4). */
public class PrecondicionRequeridaException extends RuntimeException {

    public PrecondicionRequeridaException() {
        super("Falta el header If-Match con el ETag de la ficha");
    }
}
