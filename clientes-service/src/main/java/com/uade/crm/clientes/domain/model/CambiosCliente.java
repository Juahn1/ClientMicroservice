package com.uade.crm.clientes.domain.model;

import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.clientes.domain.valor.Telefono;
import com.uade.crm.contracts.Provincia;

/**
 * Datos de una edición ya normalizados (design D2 del change 03). Todos son opcionales: un null significa
 * "no modificar". Los textos se recortan y un texto en blanco cuenta como ausente, igual que en el alta.
 */
public record CambiosCliente(
        String nombre,
        String apellido,
        Email email,
        Telefono telefono,
        Provincia provincia,
        String localidad) {

    public CambiosCliente {
        nombre = recortar(nombre);
        apellido = recortar(apellido);
        localidad = recortar(localidad);
    }

    private static String recortar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
