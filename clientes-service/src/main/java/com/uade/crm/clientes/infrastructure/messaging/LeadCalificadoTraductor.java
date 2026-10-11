package com.uade.crm.clientes.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uade.crm.clientes.config.ClientesProperties;
import com.uade.crm.clientes.domain.model.CalificacionHistorica;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Traduce un mensaje de {@code lead-calificado} a una {@link CalificacionHistorica} (design D2 y D3 del change 08).
 *
 * <p>Acepta el evento tal como lo publica hoy {@code calificacion} (JSON plano) y también envuelto en el sobre común de
 * integración, si lo adopta. Ignora los campos que no conoce. Cualquier incumplimiento lanza
 * {@link MensajeInvalidoException}, que no se reintenta.
 */
@Component
public class LeadCalificadoTraductor {

    static final String TIPO_EVENTO = "LeadCalificado";
    static final int LARGO_MAXIMO_TEXTO = 40;
    static final int LARGO_MAXIMO_OBSERVACION = 2000;

    private final ObjectMapper json;
    private final ZoneId zonaFecha;

    @Autowired
    public LeadCalificadoTraductor(ObjectMapper json, ClientesProperties properties) {
        this(json, properties.historial().zonaFecha());
    }

    LeadCalificadoTraductor(ObjectMapper json, ZoneId zonaFecha) {
        this.json = json;
        this.zonaFecha = zonaFecha;
    }

    /**
     * El mensaje traducido.
     *
     * @param correlationId el del sobre común, si vino; {@code null} con el evento plano
     */
    public record Traducido(CalificacionHistorica calificacion, String correlationId) {
    }

    public Traducido traducir(byte[] mensaje) {
        JsonNode raiz = leer(mensaje);
        JsonNode datos = raiz;
        String correlationId = null;
        if (raiz.path("payload").isObject() && raiz.path("eventType").isTextual()) {
            if (!TIPO_EVENTO.equals(raiz.get("eventType").asText())) {
                throw new MensajeInvalidoException("eventType", "no es " + TIPO_EVENTO);
            }
            datos = raiz.get("payload");
            correlationId = raiz.path("correlationId").isTextual() ? raiz.get("correlationId").asText() : null;
        }
        CalificacionHistorica calificacion = new CalificacionHistorica(
                uuid(datos, "leadId"),
                uuid(datos, "clienteId"),
                texto(datos, "nivel"),
                entero(datos, "puntos"),
                texto(datos, "origen"),
                fecha(datos),
                observacion(datos),
                motivos(datos));
        return new Traducido(calificacion, correlationId);
    }

    private JsonNode leer(byte[] mensaje) {
        if (mensaje == null) {
            throw new MensajeInvalidoException("mensaje", "está vacío");
        }
        JsonNode raiz;
        try {
            raiz = json.readTree(mensaje);
        } catch (IOException e) {
            throw new MensajeInvalidoException("mensaje", "no es JSON");
        }
        if (raiz == null || !raiz.isObject()) {
            throw new MensajeInvalidoException("mensaje", "no es un objeto JSON");
        }
        return raiz;
    }

    private static UUID uuid(JsonNode datos, String campo) {
        JsonNode nodo = obligatorio(datos, campo);
        if (!nodo.isTextual()) {
            throw new MensajeInvalidoException(campo, "no es un UUID");
        }
        try {
            return UUID.fromString(nodo.asText());
        } catch (IllegalArgumentException e) {
            throw new MensajeInvalidoException(campo, "no es un UUID");
        }
    }

    private static int entero(JsonNode datos, String campo) {
        JsonNode nodo = obligatorio(datos, campo);
        if (!nodo.isIntegralNumber() || !nodo.canConvertToInt()) {
            throw new MensajeInvalidoException(campo, "no es un entero");
        }
        return nodo.intValue();
    }

    private static String texto(JsonNode datos, String campo) {
        JsonNode nodo = obligatorio(datos, campo);
        if (!nodo.isTextual() || nodo.asText().isBlank()) {
            throw new MensajeInvalidoException(campo, "no es un texto no vacío");
        }
        String valor = nodo.asText().strip();
        if (valor.length() > LARGO_MAXIMO_TEXTO) {
            throw new MensajeInvalidoException(campo, "supera los " + LARGO_MAXIMO_TEXTO + " caracteres");
        }
        return valor;
    }

    /** Con offset o {@code Z} se respeta; sin zona se ubica en la zona configurada (design D3). */
    private Instant fecha(JsonNode datos) {
        JsonNode nodo = obligatorio(datos, "fecha");
        if (!nodo.isTextual()) {
            throw new MensajeInvalidoException("fecha", "no es una fecha ISO-8601");
        }
        String texto = nodo.asText();
        Instant instante;
        try {
            instante = OffsetDateTime.parse(texto).toInstant();
        } catch (DateTimeParseException conZona) {
            try {
                instante = LocalDateTime.parse(texto).atZone(zonaFecha).toInstant();
            } catch (DateTimeParseException sinZona) {
                throw new MensajeInvalidoException("fecha", "no es una fecha ISO-8601");
            }
        }
        // La precisión de TIMESTAMPTZ: lo que se guarda y lo que compara ON CONFLICT es el mismo valor.
        return instante.truncatedTo(ChronoUnit.MICROS);
    }

    private static String observacion(JsonNode datos) {
        JsonNode nodo = datos.get("observacion");
        if (nodo == null || nodo.isNull()) {
            return null;
        }
        if (!nodo.isTextual()) {
            throw new MensajeInvalidoException("observacion", "no es un texto");
        }
        if (nodo.asText().length() > LARGO_MAXIMO_OBSERVACION) {
            throw new MensajeInvalidoException("observacion",
                    "supera los " + LARGO_MAXIMO_OBSERVACION + " caracteres");
        }
        return nodo.asText();
    }

    private static List<String> motivos(JsonNode datos) {
        JsonNode nodo = datos.get("motivos");
        if (nodo == null || nodo.isNull()) {
            return List.of();
        }
        if (!nodo.isArray()) {
            throw new MensajeInvalidoException("motivos", "no es una lista de textos");
        }
        List<String> motivos = new ArrayList<>(nodo.size());
        for (JsonNode motivo : nodo) {
            if (!motivo.isTextual()) {
                throw new MensajeInvalidoException("motivos", "no es una lista de textos");
            }
            motivos.add(motivo.asText());
        }
        return motivos;
    }

    private static JsonNode obligatorio(JsonNode datos, String campo) {
        JsonNode nodo = datos.get(campo);
        if (nodo == null || nodo.isNull()) {
            throw new MensajeInvalidoException(campo, "es obligatorio");
        }
        return nodo;
    }
}
