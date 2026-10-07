package com.uade.crm.clientes.api.dto;

import com.uade.crm.clientes.domain.model.Coincidencia;
import com.uade.crm.clientes.domain.model.ResultadoAlta;
import java.util.UUID;

/** {@code { "clienteId": "...", "nuevo": true, "coincidencia": "NINGUNA" }} (ejemplo de §5.3 del seed). */
public record AltaClienteResponse(UUID clienteId, boolean nuevo, Coincidencia coincidencia) {

    public static AltaClienteResponse de(ResultadoAlta resultado) {
        return new AltaClienteResponse(resultado.clienteId(), resultado.nuevo(), resultado.coincidencia());
    }
}
