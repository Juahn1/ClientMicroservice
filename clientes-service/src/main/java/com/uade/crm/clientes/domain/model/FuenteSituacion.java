package com.uade.crm.clientes.domain.model;

/** De dónde salió una situación crediticia (design D3 del change 05). */
public enum FuenteSituacion {

    /** Adaptador simulado determinístico de la Primera Parte. */
    SIMULADA,

    /** Servicio de precalificación contra el BCRA (Segunda Parte, backlog {@code clientes-09}). */
    BCRA
}
