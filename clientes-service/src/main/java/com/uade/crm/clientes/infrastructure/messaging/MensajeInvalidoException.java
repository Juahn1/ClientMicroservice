package com.uade.crm.clientes.infrastructure.messaging;

/**
 * Un mensaje de {@code lead-calificado} que no se puede leer o no cumple el formato (design D2 del change 08). No se
 * reintenta: va directo al tópico de mensajes fallidos. El mensaje nombra el campo y la regla, nunca el valor, porque
 * viaja como header al tópico de fallidos y termina en los logs.
 */
public class MensajeInvalidoException extends RuntimeException {

    private final String campo;

    public MensajeInvalidoException(String campo, String regla) {
        super("Mensaje de lead-calificado inválido: '" + campo + "' " + regla);
        this.campo = campo;
    }

    public String campo() {
        return campo;
    }
}
