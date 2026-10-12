package com.uade.crm.clientes.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Confirmación de la anonimización (design D6 del change 03): el email del cliente, que se compara sin distinguir
 * mayúsculas ni espacios. Va en el cuerpo para que no quede en los logs de URLs del gateway.
 */
public record AnonimizacionRequest(
        @Schema(description = "Email registrado del cliente", example = "martina@gmail.com")
        @NotBlank @Size(max = 254)
        String email) {
}
