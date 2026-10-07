package com.uade.crm.clientes.domain.valor;

import java.util.Locale;

/**
 * Email normalizado: clave de deduplicación de clientes (design D4). La normalización es deliberadamente mínima
 * (recorte y minúsculas): quitar puntos o sufijos {@code +tag} depende del proveedor y podría unir personas
 * distintas. El formato lo valida la API con Bean Validation.
 */
public record Email(String valor) {

    public Email {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El email es obligatorio");
        }
        valor = valor.strip().toLowerCase(Locale.ROOT);
    }

    public static Email de(String crudo) {
        return new Email(crudo);
    }

    /** Conserva la primera letra y el dominio: un log accidental no expone la dirección completa. */
    @Override
    public String toString() {
        int arroba = valor.indexOf('@');
        return arroba <= 0 ? "***" : valor.charAt(0) + "***" + valor.substring(arroba);
    }
}
