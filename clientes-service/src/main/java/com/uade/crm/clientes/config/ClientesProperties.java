package com.uade.crm.clientes.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración externalizada del servicio (prefijo {@code clientes}).
 */
@Validated
@ConfigurationProperties(prefix = "clientes")
public record ClientesProperties(
        @Valid @NotNull Telefono telefono,
        @Valid @NotNull Cuil cuil,
        @Valid @NotNull SituacionCrediticia situacionCrediticia,
        @Valid @NotNull Interesados interesados,
        @Valid @NotNull Paginacion paginacion,
        @Valid @NotNull Kafka kafka,
        @Valid @NotNull Errores errores) {

    public record Telefono(@NotBlank String regionPorDefecto) {
    }

    /**
     * Claves del CUIL en base64 (§5.8). Se valida el contenido y no solo que haya texto, porque Spring Boot liga
     * un placeholder sin resolver ({@code ${CLIENTES_CUIL_CLAVE}}) como texto literal y el servicio arrancaría igual.
     */
    public record Cuil(@NotBlank String claveCifrado, @NotBlank String claveHash) {

        private static final int BYTES_AES_256 = 32;

        public Cuil {
            exigirBase64(claveCifrado, "clientes.cuil.clave-cifrado", true);
            exigirBase64(claveHash, "clientes.cuil.clave-hash", false);
        }

        private static void exigirBase64(String clave, String propiedad, boolean exacta) {
            if (clave == null || clave.isBlank()) {
                return;
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(clave);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(propiedad + " no es base64 válido (¿falta la variable de entorno?)");
            }
            if (exacta ? bytes.length != BYTES_AES_256 : bytes.length < BYTES_AES_256) {
                throw new IllegalArgumentException(propiedad + " debe tener " + (exacta ? "" : "al menos ")
                        + BYTES_AES_256 + " bytes y tiene " + bytes.length);
            }
        }

        @Override
        public String toString() {
            return "Cuil[claves=****]";
        }
    }

    public record SituacionCrediticia(
            @NotNull ModoSituacionCrediticia modo,
            @NotNull Duration vigencia,
            @NotNull Duration timeout,
            @Valid @NotNull Simulado simulado) {

        public record Simulado(@NotNull Duration demora, boolean forzarNoDisponible) {
        }
    }

    public enum ModoSituacionCrediticia {
        SIMULADO, BCRA
    }

    public record Interesados(@NotNull @DecimalMin("0") BigDecimal toleranciaPrecio) {
    }

    public record Paginacion(@Positive int tamanioMaximo) {
    }

    public record Kafka(@Valid @NotNull Topicos topicos) {

        public record Topicos(@NotBlank String clienteActualizado, @NotBlank String clienteAnonimizado) {
        }
    }

    public record Errores(@NotBlank String tipoBase) {
    }
}
