package com.uade.crm.clientes.domain.model;

import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.clientes.domain.valor.Telefono;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import java.util.Objects;

/**
 * Datos de un alta ya normalizados. {@code telefono}, {@code localidad} y {@code canal} son opcionales (null).
 */
public record SolicitudAlta(
        String nombre,
        String apellido,
        Email email,
        Telefono telefono,
        Provincia provincia,
        String localidad,
        CanalOrigen canal) {

    public SolicitudAlta {
        nombre = Objects.requireNonNull(nombre, "nombre").strip();
        apellido = Objects.requireNonNull(apellido, "apellido").strip();
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(provincia, "provincia");
        localidad = localidad == null || localidad.isBlank() ? null : localidad.strip();
    }
}
