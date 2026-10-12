package com.uade.crm.clientes.api;

import com.uade.crm.clientes.api.dto.HistorialResponse;
import com.uade.crm.clientes.application.HistorialCalificacionesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Historial de calificaciones del cliente, para la ficha del lead en el front (change 08). Sin clave de servicio. */
@RestController
@RequestMapping("/clientes")
@Tag(name = "Historial de calificaciones", description = "Calificaciones de todos los leads del cliente")
public class HistorialController {

    private final HistorialCalificacionesService historial;

    public HistorialController(HistorialCalificacionesService historial) {
        this.historial = historial;
    }

    @GetMapping("/{id}/historial")
    @Operation(summary = "Historial de calificaciones del cliente",
            description = "Todas las calificaciones automáticas y manuales de los leads del cliente, la calculada más "
                    + "recientemente primero y sin paginar. Se alimenta del evento LeadCalificado de calificacion, "
                    + "así que una calificación recién calculada puede tardar unos segundos en aparecer.")
    @ApiResponse(responseCode = "200", description = "Historial del cliente; lista vacía si todavía no tiene "
            + "calificaciones")
    @ApiResponse(responseCode = "404", description = "CLIENTE_NO_ENCONTRADO: el ID nunca existió")
    @ApiResponse(responseCode = "410", description = "CLIENTE_ANONIMIZADO: el cliente fue anonimizado")
    public HistorialResponse consultar(@PathVariable UUID id) {
        return HistorialResponse.de(id, historial.consultar(id));
    }
}
