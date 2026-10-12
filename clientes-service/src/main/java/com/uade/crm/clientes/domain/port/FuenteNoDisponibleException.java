package com.uade.crm.clientes.domain.port;

/**
 * La fuente de la situación crediticia falló o no respondió dentro del timeout (design D5 del change 05). Toda fuente
 * la lanza en lugar de sus propias excepciones; la resiliencia la traduce a {@code NO_DISPONIBLE}. El mensaje no
 * incluye el CUIL.
 */
public class FuenteNoDisponibleException extends RuntimeException {

    public FuenteNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public FuenteNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
