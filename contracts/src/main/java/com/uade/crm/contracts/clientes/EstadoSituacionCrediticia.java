package com.uade.crm.contracts.clientes;

/**
 * Estado de la situación crediticia que devuelve {@code GET /clientes/{id}/situacion-crediticia} (AC-05 del documento
 * semilla de clientes). Permite distinguir "no hay datos" de "hay datos malos" sin interpretar errores: la consulta
 * responde 200 con alguno de estos estados aunque la fuente externa falle.
 *
 * <p>Mapeo sugerido para la {@code ReglaFinanciacion} de calificacion: {@link #CON_DATOS} con situación 1 o 2 suma 20
 * puntos; {@link #CON_DATOS} con situación 3 a 6 resta 25; cualquier otro estado suma 0.
 */
public enum EstadoSituacionCrediticia {

    /** La fuente informó una situación de 1 a 6 para el CUIL del cliente. */
    CON_DATOS,

    /** La fuente no tiene registros para el CUIL del cliente. */
    SIN_REGISTROS,

    /** El cliente no tiene CUIL registrado: no pidió financiación. No se consulta la fuente. */
    SIN_CUIL,

    /**
     * El cliente tiene CUIL pero no tiene vigente el consentimiento de precalificación crediticia. Reservado: hoy un
     * cliente tiene CUIL si y solo si tiene ese consentimiento vigente, así que este estado recién aparece cuando el
     * titular pueda revocarlo (backlog {@code clientes-15}). Los consumidores ya deben tratarlo.
     */
    SIN_CONSENTIMIENTO,

    /** La fuente falló, no respondió a tiempo o está temporalmente fuera de servicio. Conviene reintentar más tarde. */
    NO_DISPONIBLE
}
