package com.uade.crm.clientes.api.dto;

import com.uade.crm.clientes.domain.ClienteFactory;
import com.uade.crm.clientes.domain.model.SolicitudAlta;
import com.uade.crm.contracts.CanalOrigen;
import com.uade.crm.contracts.Provincia;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta de cliente que envía {@code leads} (design D9). Nombre y apellido, ya recortados: al menos dos caracteres,
 * empiezan y terminan con letra, y en el medio admiten espacios, apóstrofo y guion
 * ("María José", "D'Angelo", "Pérez-Gómez").
 */
public record AltaClienteRequest(
        @Schema(example = "Martina")
        @NotBlank @Size(max = 120) @Pattern(regexp = NOMBRE, message = MENSAJE_NOMBRE)
        String nombre,

        @Schema(example = "López")
        @NotBlank @Size(max = 120) @Pattern(regexp = NOMBRE, message = MENSAJE_NOMBRE)
        String apellido,

        @Schema(example = "martina@gmail.com")
        @NotBlank @Size(max = 254) @Email(regexp = EMAIL, message = MENSAJE_EMAIL)
        String email,

        @Schema(description = "Opcional. Se normaliza a móvil argentino en E.164.", example = "11 1234-5678")
        @Size(max = 30)
        String telefono,

        @NotNull
        Provincia provincia,

        @Schema(example = "San Isidro")
        @Size(max = 120)
        String localidad,

        @Schema(description = "Opcional mientras el preformulario no lo informe.")
        CanalOrigen canalOrigen) {

    static final String NOMBRE = "\\p{L}[\\p{L} '’-]*\\p{L}";
    static final String MENSAJE_NOMBRE =
            "debe tener al menos 2 letras y solo puede contener letras, espacios, apóstrofo o guion";
    static final String EMAIL = ".+@.+\\..+";
    static final String MENSAJE_EMAIL = "debe ser un email válido con dominio";

    /** Se recorta antes de validar: " Martina@Gmail.com " es un email válido y el largo se mide sin espacios. */
    public AltaClienteRequest {
        nombre = recortar(nombre);
        apellido = recortar(apellido);
        email = recortar(email);
        telefono = recortar(telefono);
        localidad = recortar(localidad);
    }

    private static String recortar(String texto) {
        return texto == null ? null : texto.strip();
    }

    public SolicitudAlta aSolicitud(ClienteFactory factory) {
        return factory.solicitud(nombre, apellido, email, telefono, provincia, localidad, canalOrigen);
    }
}
