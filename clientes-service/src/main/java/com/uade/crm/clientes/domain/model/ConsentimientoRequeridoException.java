package com.uade.crm.clientes.domain.model;

/** Se informó un CUIL sin el consentimiento de precalificación que habilita su uso (Ley 25.326). */
public class ConsentimientoRequeridoException extends RuntimeException {

    public ConsentimientoRequeridoException() {
        super("Para registrar el CUIL hace falta el consentimiento de precalificación crediticia");
    }
}
