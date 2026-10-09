package com.uade.crm.clientes.domain;

import com.uade.crm.clientes.domain.model.CambiosCliente;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.SolicitudAlta;
import com.uade.crm.clientes.domain.valor.Email;
import com.uade.crm.clientes.domain.valor.Telefono;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Patrón Factory: normaliza los datos de un alta y crea clientes válidos (§5.12 del documento semilla). Concentra
 * en un lugar las reglas de un cliente nuevo: teléfono móvil en E.164, email normalizado, nombre recortado,
 * un contacto y el canal de origen como último canal. Sin dependencias de Spring.
 */
public class ClienteFactory {

    private final String regionTelefono;
    private final Clock reloj;

    public ClienteFactory(String regionTelefono, Clock reloj) {
        this.regionTelefono = regionTelefono;
        this.reloj = reloj;
    }

    /** Normaliza los datos crudos; lanza {@code TelefonoInvalidoException} si el teléfono informado no es válido. */
    public SolicitudAlta solicitud(String nombre, String apellido, String email, String telefono,
            Provincia provincia, String localidad, CanalOrigen canal) {
        return new SolicitudAlta(nombre, apellido, Email.de(email),
                Telefono.opcional(telefono, regionTelefono).orElse(null), provincia, localidad, canal);
    }

    /**
     * Normaliza los datos de una edición con las mismas reglas que el alta (design D2 del change 03). Un valor null
     * o en blanco queda null ("no modificar"); un teléfono informado inválido lanza {@code TelefonoInvalidoException}.
     */
    public CambiosCliente cambios(String nombre, String apellido, String email, String telefono, Provincia provincia,
            String localidad) {
        return new CambiosCliente(nombre, apellido,
                email == null || email.isBlank() ? null : Email.de(email),
                Telefono.opcional(telefono, regionTelefono).orElse(null), provincia, localidad);
    }

    public Cliente crear(SolicitudAlta solicitud) {
        Instant ahora = reloj.instant();
        return Cliente.builder()
                .id(UUID.randomUUID())
                .nombre(solicitud.nombre())
                .apellido(solicitud.apellido())
                .email(solicitud.email())
                .telefono(solicitud.telefono())
                .provincia(solicitud.provincia())
                .localidad(solicitud.localidad())
                .canalOrigen(solicitud.canal())
                .ultimoCanal(solicitud.canal())
                .contactosRegistrados(1)
                .ultimoContactoEn(ahora)
                .creadoEn(ahora)
                .actualizadoEn(ahora)
                .build();
    }
}
