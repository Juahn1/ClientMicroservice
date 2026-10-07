package com.uade.crm.clientes.api.error;

import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Códigos estables del campo {@code codigo} de las respuestas de error (catálogo §5.4 del documento semilla).
 */
public enum CodigoError {

    VALIDACION(HttpStatus.UNPROCESSABLE_ENTITY, "Datos inválidos"),
    JSON_INVALIDO(HttpStatus.BAD_REQUEST, "Cuerpo mal formado"),
    TELEFONO_INVALIDO(HttpStatus.UNPROCESSABLE_ENTITY, "Teléfono inválido"),
    CLAVE_SERVICIO_INVALIDA(HttpStatus.UNAUTHORIZED, "Clave de servicio inválida"),
    CLIENTE_NO_ENCONTRADO(HttpStatus.NOT_FOUND, "Cliente no encontrado"),
    /** Errores 4xx que resuelve Spring MVC y no están en el catálogo (método no permitido, ruta inexistente, etc.). */
    SOLICITUD_INVALIDA(HttpStatus.BAD_REQUEST, "Solicitud inválida"),
    ERROR_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno");

    private final HttpStatus status;
    private final String titulo;

    CodigoError(HttpStatus status, String titulo) {
        this.status = status;
        this.titulo = titulo;
    }

    public HttpStatus status() {
        return status;
    }

    public String titulo() {
        return titulo;
    }

    /** {@code CLIENTE_NO_ENCONTRADO} → {@code cliente-no-encontrado}, usado en el {@code type} del ProblemDetail. */
    public String slug() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
