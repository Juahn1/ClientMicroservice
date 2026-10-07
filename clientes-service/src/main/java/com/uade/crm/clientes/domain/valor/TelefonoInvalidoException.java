package com.uade.crm.clientes.domain.valor;

/** El teléfono no se puede normalizar a un móvil argentino. El mensaje no incluye el número (dato personal). */
public class TelefonoInvalidoException extends RuntimeException {

    public TelefonoInvalidoException() {
        super("El teléfono no es un móvil argentino válido");
    }
}
