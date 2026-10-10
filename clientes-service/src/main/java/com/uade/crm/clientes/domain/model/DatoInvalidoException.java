package com.uade.crm.clientes.domain.model;

/**
 * Un dato no cumple una regla del dominio que Bean Validation no puede expresar (por ejemplo, porque depende del
 * estado del cliente). Se responde 422 {@code VALIDACION} asociado al campo (design D4 del change 04). El mensaje
 * no incluye el valor recibido.
 */
public class DatoInvalidoException extends RuntimeException {

    private final String campo;

    public DatoInvalidoException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String campo() {
        return campo;
    }
}
