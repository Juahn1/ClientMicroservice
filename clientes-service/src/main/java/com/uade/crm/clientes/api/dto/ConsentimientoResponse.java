package com.uade.crm.clientes.api.dto;

import com.uade.crm.clientes.domain.model.Consentimiento;
import com.uade.crm.clientes.domain.model.FinalidadConsentimiento;
import java.time.Instant;

/** Consentimiento vigente en la ficha (design D7 del change 04). */
public record ConsentimientoResponse(FinalidadConsentimiento finalidad, String versionTexto, Instant otorgadoEn) {

    public static ConsentimientoResponse de(Consentimiento c) {
        return new ConsentimientoResponse(c.finalidad(), c.versionTexto(), c.otorgadoEn());
    }
}
