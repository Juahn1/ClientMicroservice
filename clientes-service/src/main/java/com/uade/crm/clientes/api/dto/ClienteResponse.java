package com.uade.crm.clientes.api.dto;

import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.valor.Telefono;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import java.time.Instant;
import java.util.UUID;

/**
 * Ficha del cliente (design D7). El teléfono va completo porque el telemarketer lo necesita para contactar;
 * {@code cuil} y {@code consentimientosVigentes} se agregan en el change 04.
 */
public record ClienteResponse(
        UUID id,
        String nombre,
        String apellido,
        String email,
        String telefono,
        String telefonoAlternativo,
        Provincia provincia,
        String localidad,
        CanalOrigen canalOrigen,
        CanalOrigen ultimoCanal,
        int contactosRegistrados,
        Instant ultimoContactoEn,
        boolean requiereRevision) {

    public static ClienteResponse de(Cliente c) {
        return new ClienteResponse(
                c.id(),
                c.nombre(),
                c.apellido(),
                c.email().valor(),
                c.telefono().map(Telefono::e164).orElse(null),
                c.telefonoAlternativo().map(Telefono::e164).orElse(null),
                c.provincia(),
                c.localidad().orElse(null),
                c.canalOrigen().orElse(null),
                c.ultimoCanal().orElse(null),
                c.contactosRegistrados(),
                c.ultimoContactoEn(),
                c.requiereRevision());
    }
}
