package com.uade.crm.clientes.domain.model;

import com.uade.crm.contracts.clientes.CampoCliente;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Qué cambió en una edición (design D7 del change 04). {@code huboCambios} decide si se guarda y cambia el
 * {@code ETag}; {@code camposPublicables} decide si se publica {@code ClienteActualizado}. Son distintos porque el
 * teléfono alternativo y la marca de revisión cambian la ficha pero no son datos que copie {@code leads}.
 */
public record ResultadoEdicion(boolean huboCambios, Set<CampoCliente> camposPublicables) {

    public ResultadoEdicion {
        // EnumSet conserva el orden del enum, que es el orden de campos[] en ClienteActualizado.
        camposPublicables = camposPublicables.isEmpty() ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(camposPublicables));
        if (!huboCambios && !camposPublicables.isEmpty()) {
            throw new IllegalArgumentException("Hay campos publicables sin cambios");
        }
    }
}
