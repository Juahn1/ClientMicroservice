package com.uade.crm.clientes.infrastructure.messaging;

import com.uade.crm.clientes.domain.port.PublicadorEventos;
import com.uade.crm.contracts.EventoIntegracion;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Adaptador de {@link PublicadorEventos} sobre Kafka (design D8 del change 03). La clave del mensaje es el
 * {@code clienteId}, así los eventos de un mismo cliente quedan en orden dentro de su partición. El envío es
 * asincrónico: una falla se loguea con el {@code clienteId} y el tipo de evento, nunca con datos personales.
 */
@Component
class PublicadorEventosKafka implements PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorEventosKafka.class);

    private final KafkaTemplate<String, Object> kafka;

    PublicadorEventosKafka(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void publicar(String topico, UUID clave, EventoIntegracion<?> evento) {
        try {
            kafka.send(topico, clave.toString(), evento).whenComplete((resultado, falla) -> {
                if (falla != null) {
                    log.error("Falló la publicación de {} del cliente {} en {}", evento.eventType(), clave, topico,
                            falla);
                } else {
                    log.info("Publicado {} del cliente {} en {}", evento.eventType(), clave, topico);
                }
            });
        } catch (RuntimeException falla) {
            // El productor puede fallar antes de devolver el future (por ejemplo, sin metadata del broker
            // dentro de max.block.ms).
            log.error("Falló la publicación de {} del cliente {} en {}", evento.eventType(), clave, topico, falla);
        }
    }
}
