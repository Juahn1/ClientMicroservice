package com.uade.crm.clientes.domain.model;

import java.util.UUID;

/** Resultado de un alta: el cliente creado o el existente con el que coincidió. */
public record ResultadoAlta(UUID clienteId, boolean nuevo, Coincidencia coincidencia) {
}
