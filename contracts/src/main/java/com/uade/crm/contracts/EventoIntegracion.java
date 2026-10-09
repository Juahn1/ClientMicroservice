package com.uade.crm.contracts;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sobre común de los eventos de integración que viajan por Kafka (seed §5.5). El {@code payload} lleva solo
 * identificadores y nombres de campos, nunca datos personales.
 *
 * @param eventId       identificador único del mensaje, para que el consumidor descarte duplicados
 * @param eventType     nombre del evento, por ejemplo {@code ClienteActualizado}
 * @param version       versión del esquema del payload
 * @param occurredAt    momento en que ocurrió el hecho
 * @param correlationId el {@code X-Correlation-Id} del request que lo originó
 * @param payload       contenido propio del evento
 */
public record EventoIntegracion<T>(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String correlationId,
        T payload) {

    public EventoIntegracion {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(payload, "payload");
    }
}
