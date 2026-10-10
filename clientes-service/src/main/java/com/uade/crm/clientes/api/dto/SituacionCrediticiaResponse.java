package com.uade.crm.clientes.api.dto;

import com.uade.crm.clientes.domain.model.FuenteSituacion;
import com.uade.crm.clientes.domain.model.SituacionCrediticia;
import com.uade.crm.contracts.clientes.EstadoSituacionCrediticia;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Respuesta de {@code GET /clientes/{id}/situacion-crediticia} (design D3 del change 05). La forma no cambia según el
 * estado: los campos que no aplican van en {@code null}. Nunca incluye el CUIL.
 */
public record SituacionCrediticiaResponse(
        UUID clienteId,
        @Schema(description = "Estado explícito. SIN_CONSENTIMIENTO está reservado: hoy no se produce.")
        EstadoSituacionCrediticia estado,
        @Schema(description = "Situación de 1 (normal) a 6 (irrecuperable); solo con estado CON_DATOS",
                minimum = "1", maximum = "6", nullable = true)
        Integer situacion,
        @Schema(description = "Período informado por la fuente (AAAAMM); solo con CON_DATOS o SIN_REGISTROS",
                example = "202609", nullable = true)
        String periodo,
        @Schema(description = "Fuente consultada; solo con CON_DATOS o SIN_REGISTROS", nullable = true)
        FuenteSituacion fuente,
        @Schema(description = "Cuándo se consultó la fuente; puede ser anterior al request si viene de la caché",
                nullable = true)
        Instant consultadaEn) {

    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("yyyyMM");

    public static SituacionCrediticiaResponse de(UUID clienteId, SituacionCrediticia s) {
        return new SituacionCrediticiaResponse(clienteId, s.estado(), s.situacion(),
                s.periodo() == null ? null : PERIODO.format(s.periodo()), s.fuente(), s.consultadaEn());
    }
}
