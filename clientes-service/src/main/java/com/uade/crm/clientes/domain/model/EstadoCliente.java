package com.uade.crm.clientes.domain.model;

public enum EstadoCliente {
    ACTIVO,
    /** Baja por derecho de supresión (change 03): se conservan id y datos estadísticos, sin datos personales. */
    ANONIMIZADO
}
