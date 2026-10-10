package com.uade.crm.clientes.api.seguridad;

/**
 * Servicio que presenta una clave en {@code X-Api-Key} (design D7 del change 05). Cada uno tiene su propia clave y
 * solo abre los endpoints anotados con su nombre.
 */
public enum Consumidor {

    /** Alta de clientes desde el preformulario. */
    LEADS,

    /** Consulta de la situación crediticia para la calificación del lead. */
    CALIFICACION
}
