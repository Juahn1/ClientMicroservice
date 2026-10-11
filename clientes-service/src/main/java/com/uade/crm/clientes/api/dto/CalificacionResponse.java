package com.uade.crm.clientes.api.dto;

import com.uade.crm.clientes.domain.model.CalificacionHistorica;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Una calificación del historial (design D8 del change 08). Usa los nombres del contrato del equipo, no los del evento
 * de {@code calificacion} ({@code puntos}, {@code fecha}).
 */
public record CalificacionResponse(
        @Schema(description = "Lead calificado: un cliente puede tener varios")
        UUID leadId,
        @Schema(description = "Nivel que asignó calificacion. Valores conocidos: MUY_POTABLE, POTABLE, NO_POTABLE; "
                + "puede llegar otro si calificacion lo agrega", example = "MUY_POTABLE")
        String nivel,
        @Schema(description = "Puntaje total; puede ser negativo", example = "60")
        int puntaje,
        @Schema(description = "AUTOMATICA (motor de reglas) o MANUAL (corrección de un asesor); puede llegar otro "
                + "valor si calificacion lo agrega", example = "AUTOMATICA")
        String origen,
        @Schema(description = "Cuándo se calculó o corrigió, en UTC")
        Instant calculadaEn,
        @Schema(description = "Detalle de la calificación o motivo de la corrección", nullable = true)
        String observacion,
        @Schema(description = "Desglose de cada regla evaluada, en el orden de calificacion; puede estar vacío")
        List<String> motivos) {

    public static CalificacionResponse de(CalificacionHistorica c) {
        return new CalificacionResponse(c.leadId(), c.nivel(), c.puntaje(), c.origen(), c.calculadaEn(),
                c.observacion(), c.motivos());
    }
}
