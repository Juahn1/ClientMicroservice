package com.uade.crm.clientes.api;

import com.uade.crm.clientes.api.dto.SituacionCrediticiaResponse;
import com.uade.crm.clientes.api.seguridad.Consumidor;
import com.uade.crm.clientes.api.seguridad.RequiereClaveServicio;
import com.uade.crm.clientes.application.SituacionCrediticiaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Situación crediticia del cliente para {@code calificacion} (change 05). */
@RestController
@RequestMapping("/clientes")
@Tag(name = "Situación crediticia", description = "Situación crediticia del cliente, solo para calificacion")
public class SituacionCrediticiaController {

    private final SituacionCrediticiaService situaciones;

    public SituacionCrediticiaController(SituacionCrediticiaService situaciones) {
        this.situaciones = situaciones;
    }

    @GetMapping("/{id}/situacion-crediticia")
    @RequiereClaveServicio(Consumidor.CALIFICACION)
    @SecurityRequirement(name = "claveServicio")
    @Operation(summary = "Situación crediticia del cliente",
            description = "Solo para calificacion, con su clave en X-Api-Key. Responde 200 con un `estado` "
                    + "explícito aunque la fuente falle: CON_DATOS (con `situacion` de 1 a 6), SIN_REGISTROS, "
                    + "SIN_CUIL (el cliente no pidió financiación) o NO_DISPONIBLE (reintentar más tarde). Los "
                    + "resultados de la fuente se guardan 30 días. El timeout de la fuente es de 2 s: el cliente "
                    + "tiene que esperar algo más. Nunca incluye el CUIL.")
    @ApiResponse(responseCode = "200", description = "Situación crediticia con su estado")
    @ApiResponse(responseCode = "401", description = "CLAVE_SERVICIO_INVALIDA: falta X-Api-Key o no es la de "
            + "calificacion")
    @ApiResponse(responseCode = "404", description = "CLIENTE_NO_ENCONTRADO: el ID nunca existió")
    @ApiResponse(responseCode = "410", description = "CLIENTE_ANONIMIZADO: el cliente fue anonimizado")
    public SituacionCrediticiaResponse consultar(@PathVariable UUID id) {
        return SituacionCrediticiaResponse.de(id, situaciones.consultar(id));
    }
}
