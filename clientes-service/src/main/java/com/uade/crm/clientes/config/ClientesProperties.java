package com.uade.crm.clientes.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración externalizada del servicio (prefijo {@code clientes}).
 */
@Validated
@ConfigurationProperties(prefix = "clientes")
public record ClientesProperties(
        @Valid @NotNull Seguridad seguridad,
        @Valid @NotNull Telefono telefono,
        @Valid @NotNull Cuil cuil,
        @Valid @NotNull Consentimiento consentimiento,
        @Valid @NotNull SituacionCrediticia situacionCrediticia,
        @Valid @NotNull Interesados interesados,
        @Valid @NotNull Paginacion paginacion,
        @Valid @NotNull Kafka kafka,
        @Valid @NotNull Historial historial,
        @Valid @NotNull Errores errores) {

    /**
     * Claves que presentan los servicios en {@code X-Api-Key}, una por consumidor (design D2 del change 02 y D7 del
     * change 05): {@code leads} para el alta y {@code calificacion} para la situación crediticia. Se exige un largo
     * mínimo para que un placeholder sin resolver ({@code ${CLIENTES_CLAVE_LEADS}}) no deje el endpoint protegido por
     * una clave conocida, y que sean distintas para que la clave de un servicio no abra los endpoints del otro.
     */
    public record Seguridad(@NotBlank String claveLeads, @NotBlank String claveCalificacion) {

        public static final int LARGO_MINIMO = 32;

        public Seguridad {
            exigirClave(claveLeads, "clientes.seguridad.clave-leads", "CLIENTES_CLAVE_LEADS");
            exigirClave(claveCalificacion, "clientes.seguridad.clave-calificacion", "CLIENTES_CLAVE_CALIFICACION");
            if (claveLeads != null && claveLeads.equals(claveCalificacion)) {
                throw new IllegalArgumentException(
                        "clientes.seguridad.clave-leads y clientes.seguridad.clave-calificacion deben ser distintas");
            }
        }

        private static void exigirClave(String clave, String propiedad, String variable) {
            if (clave == null || clave.isBlank()) {
                return;
            }
            if (clave.startsWith("${")) {
                throw new IllegalArgumentException(
                        propiedad + " no está resuelta (¿falta la variable de entorno " + variable + "?)");
            }
            if (clave.length() < LARGO_MINIMO) {
                throw new IllegalArgumentException(propiedad + " debe tener al menos " + LARGO_MINIMO
                        + " caracteres y tiene " + clave.length() + " (¿falta la variable de entorno " + variable + "?)");
            }
        }

        @Override
        public String toString() {
            return "Seguridad[claveLeads=****, claveCalificacion=****]";
        }
    }

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

    /** Versión del texto legal del consentimiento que se registra en cada alta con CUIL (design D4 del change 04). */
    public record Consentimiento(@NotBlank @Size(max = 20) String versionTexto) {
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

    public record Paginacion(@Positive int tamanioMaximo, @Positive int tamanioPorDefecto) {

        public Paginacion {
            if (tamanioPorDefecto > tamanioMaximo) {
                throw new IllegalArgumentException("clientes.paginacion.tamanio-por-defecto (" + tamanioPorDefecto
                        + ") no puede superar clientes.paginacion.tamanio-maximo (" + tamanioMaximo + ")");
            }
        }
    }

    /**
     * Tópicos que publica y consume el servicio (design D8 del change 03 y D9 del change 08). Particiones y réplicas
     * se usan al crear los tópicos propios, incluido el de mensajes fallidos; {@code leadCalificado} es de
     * {@code calificacion} y el servicio no lo crea.
     */
    public record Kafka(@Valid @NotNull Topicos topicos, @Positive int particiones, @Positive int replicas) {

        public record Topicos(
                @NotBlank String clienteActualizado,
                @NotBlank String clienteAnonimizado,
                @NotBlank String leadCalificado,
                @NotBlank String leadCalificadoDlt) {
        }
    }

    /**
     * Historial de calificaciones (design D3, D5 y D9 del change 08). {@code zonaFecha} interpreta la fecha de
     * {@code LeadCalificado} cuando llega sin zona; {@code reintentos} acota los reintentos de una falla transitoria
     * antes de mandar el mensaje al tópico de fallidos.
     */
    public record Historial(@NotNull ZoneId zonaFecha, @Valid @NotNull Reintentos reintentos) {

        /** Espera antes del reintento n (desde 0): {@code esperaInicial * multiplicador^n}. */
        public record Reintentos(
                @PositiveOrZero int intentos,
                @NotNull Duration esperaInicial,
                @DecimalMin("1.0") double multiplicador) {
        }
    }

    public record Errores(@NotBlank String tipoBase) {
    }
}
