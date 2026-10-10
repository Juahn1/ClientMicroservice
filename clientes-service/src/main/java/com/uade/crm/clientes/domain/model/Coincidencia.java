package com.uade.crm.clientes.domain.model;

/** Por qué un alta se resolvió como un cliente existente (design D5 del change 04: primero CUIL, después email). */
public enum Coincidencia {
    NINGUNA,
    EMAIL,
    CUIL
}
