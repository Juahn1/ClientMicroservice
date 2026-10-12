package com.uade.crm.clientes.api.dto;

import com.uade.crm.clientes.domain.model.CalificacionHistorica;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de {@code GET /clientes/{id}/historial} (design D8 del change 08). Un objeto y no una lista suelta: deja
 * lugar para sumar campos (por ejemplo, paginación) sin cambiar el tipo de la respuesta.
 */
public record HistorialResponse(
        UUID clienteId,
        @Schema(description = "Todas las calificaciones del cliente, la calculada más recientemente primero")
        List<CalificacionResponse> calificaciones) {

    public static HistorialResponse de(UUID clienteId, List<CalificacionHistorica> calificaciones) {
        return new HistorialResponse(clienteId, calificaciones.stream().map(CalificacionResponse::de).toList());
    }
}
