package com.uade.crm.clientes.api.dto;

import static com.uade.crm.clientes.api.dto.AltaClienteRequest.EMAIL;
import static com.uade.crm.clientes.api.dto.AltaClienteRequest.MENSAJE_EMAIL;
import static com.uade.crm.clientes.api.dto.AltaClienteRequest.MENSAJE_NOMBRE;
import static com.uade.crm.clientes.api.dto.AltaClienteRequest.NOMBRE;

import com.uade.crm.clientes.domain.ClienteFactory;
import com.uade.crm.clientes.domain.model.CambiosCliente;
import com.uade.crm.contracts.Provincia;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Edición parcial de un cliente (design D4 del change 03). Todos los campos son opcionales: ausente, null o en
 * blanco significa "no modificar". Los informados cumplen las mismas reglas que el alta.
 */
public record EdicionClienteRequest(
        @Schema(example = "Martina")
        @Size(max = 120) @Pattern(regexp = NOMBRE, message = MENSAJE_NOMBRE)
        String nombre,

        @Schema(example = "Gómez")
        @Size(max = 120) @Pattern(regexp = NOMBRE, message = MENSAJE_NOMBRE)
        String apellido,

        @Schema(example = "martina.gomez@gmail.com")
        @Size(max = 254) @Email(regexp = EMAIL, message = MENSAJE_EMAIL)
        String email,

        @Schema(description = "Se normaliza a móvil argentino en E.164.", example = "11 1234-5678")
        @Size(max = 30)
        String telefono,

        Provincia provincia,

        @Schema(example = "San Isidro")
        @Size(max = 120)
        String localidad) {

    /** Se recorta antes de validar, y un texto en blanco cuenta como ausente (no falla el patrón de nombre). */
    public EdicionClienteRequest {
        nombre = normalizar(nombre);
        apellido = normalizar(apellido);
        email = normalizar(email);
        telefono = normalizar(telefono);
        localidad = normalizar(localidad);
    }

    private static String normalizar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    public CambiosCliente aCambios(ClienteFactory factory) {
        return factory.cambios(nombre, apellido, email, telefono, provincia, localidad);
    }
}
