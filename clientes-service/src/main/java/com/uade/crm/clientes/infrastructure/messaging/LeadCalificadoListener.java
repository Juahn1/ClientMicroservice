package com.uade.crm.clientes.infrastructure.messaging;

import com.uade.crm.clientes.application.RegistroCalificacionService;
import com.uade.crm.clientes.config.CorrelationIdFilter;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor de {@code lead-calificado} (design D7 del change 08): traduce el mensaje y lo registra en el historial.
 * No captura excepciones: los reintentos y el tópico de fallidos los decide {@link ConsumidorKafkaConfig}.
 *
 * <p>Cada mensaje se procesa con su propio {@code correlationId} en el MDC: el del sobre común si vino y tiene formato
 * válido, o uno nuevo. Los logs identifican el mensaje por partición y offset, nunca con su contenido.
 */
@Component
class LeadCalificadoListener {

    private static final Logger log = LoggerFactory.getLogger(LeadCalificadoListener.class);

    private final LeadCalificadoTraductor traductor;
    private final RegistroCalificacionService registro;

    LeadCalificadoListener(LeadCalificadoTraductor traductor, RegistroCalificacionService registro) {
        this.traductor = traductor;
        this.registro = registro;
    }

    @KafkaListener(topics = "${clientes.kafka.topicos.lead-calificado}")
    void recibir(ConsumerRecord<String, byte[]> mensaje) {
        MDC.put(CorrelationIdFilter.MDC_KEY, UUID.randomUUID().toString());
        try {
            log.debug("Mensaje de {} recibido: partición {}, offset {}", mensaje.topic(), mensaje.partition(),
                    mensaje.offset());
            LeadCalificadoTraductor.Traducido traducido = traductor.traducir(mensaje.value());
            if (CorrelationIdFilter.formatoValido(traducido.correlationId())) {
                MDC.put(CorrelationIdFilter.MDC_KEY, traducido.correlationId());
            }
            registro.registrar(traducido.calificacion());
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
