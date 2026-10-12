package com.uade.crm.clientes.domain.model;

/** Por qué un alta marcó al cliente para que lo revise el personal (design D5 del change 04). */
public enum MotivoRevision {
    /** El email coincide con un cliente que tiene otro CUIL: puede ser un familiar que comparte el email. */
    CUIL_DISTINTO,
    /** El CUIL es de este cliente y el email, de otro: pueden ser la misma persona con dos fichas. */
    POSIBLE_DUPLICADO
}
