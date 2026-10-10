package com.uade.crm.clientes.domain.model;

import com.uade.crm.clientes.domain.valor.CuilProtegido;
import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.clientes.domain.valor.Telefono;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import java.util.Objects;
import java.util.Optional;

/**
 * Datos de un alta ya normalizados. {@code telefono}, {@code localidad}, {@code canal} y {@code cuil} son opcionales
 * (null). Un {@code cuil} presente implica que el titular otorgó el consentimiento de precalificación: la fábrica no
 * arma una solicitud con CUIL sin él (design D4 del change 04).
 */
public record SolicitudAlta(
        String nombre,
        String apellido,
        Email email,
        Telefono telefono,
        Provincia provincia,
        String localidad,
        CanalOrigen canal,
        CuilProtegido cuil) {

    public SolicitudAlta {
        nombre = Objects.requireNonNull(nombre, "nombre").strip();
        apellido = Objects.requireNonNull(apellido, "apellido").strip();
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(provincia, "provincia");
        localidad = localidad == null || localidad.isBlank() ? null : localidad.strip();
    }

    /** Alta sin CUIL. */
    public SolicitudAlta(String nombre, String apellido, Email email, Telefono telefono, Provincia provincia,
            String localidad, CanalOrigen canal) {
        this(nombre, apellido, email, telefono, provincia, localidad, canal, null);
    }

    public Optional<CuilProtegido> cuilInformado() {
        return Optional.ofNullable(cuil);
    }

    /** Sin datos personales: un log accidental de la solicitud no expone nombre, email, teléfono ni CUIL. */
    @Override
    public String toString() {
        return "SolicitudAlta[provincia=" + provincia + ", canal=" + canal + ", conCuil=" + (cuil != null) + "]";
    }
}
