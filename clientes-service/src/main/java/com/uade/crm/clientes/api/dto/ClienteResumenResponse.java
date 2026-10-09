package com.uade.crm.clientes.api.dto;

import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.valor.Telefono;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import java.time.Instant;
import java.util.UUID;

/**
 * Resumen de un cliente en los resultados de búsqueda (design D8). Es deliberadamente más chico que la ficha:
 * cuando el change 04 sume el CUIL a la ficha, no va a aparecer en las listas. Incluye {@code noContactar} para que
 * el telemarketer vea en la lista a quién no llamar (change 03).
 */
public record ClienteResumenResponse(
        UUID id,
        String nombre,
        String apellido,
        String email,
        String telefono,
        Provincia provincia,
        CanalOrigen ultimoCanal,
        int contactosRegistrados,
        Instant ultimoContactoEn,
        boolean noContactar) {

    public static ClienteResumenResponse de(Cliente c) {
        return new ClienteResumenResponse(
                c.id(),
                c.nombre(),
                c.apellido(),
                c.email().valor(),
                c.telefono().map(Telefono::e164).orElse(null),
                c.provincia(),
                c.ultimoCanal().orElse(null),
                c.contactosRegistrados(),
                c.ultimoContactoEn(),
                c.noContactar());
    }
}
