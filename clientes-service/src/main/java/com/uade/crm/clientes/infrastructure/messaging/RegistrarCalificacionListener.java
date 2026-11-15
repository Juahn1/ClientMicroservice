package com.uade.crm.clientes.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uade.crm.clientes.application.RegistroCalificacionService;
import com.uade.crm.clientes.config.CorrelationIdFilter;
import java.io.IOException;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Atiende el comando {@code RegistrarCalificacion} que publica el orquestador en {@code crm.comandos}
 * (docs/08-contrato-tres-topicos.md del orquestador; design D7 del change 08): traduce el mensaje y lo registra en el
 * historial. Todos los servicios leen el mismo tópico, así que los comandos cuyo {@code destino} no es
 * {@code clientes} se ignoran sin tocarlos.
 *
 * <p>No captura excepciones: los reintentos y el tópico de fallidos los decide {@link ConsumidorKafkaConfig}. Un mensaje
 * que no es JSON no se puede ignorar por destino: sigue de largo hasta el traductor, que lo rechaza y va a la DLT.
 *
 * <p>Cada mensaje se procesa con su propio {@code correlationId} en el MDC: el del sobre común si vino y tiene formato
 * válido, o uno nuevo. Los logs identifican el mensaje por partición y offset, nunca con su contenido.
 */
@Component
class RegistrarCalificacionListener {

    private static final Logger log = LoggerFactory.getLogger(RegistrarCalificacionListener.class);

    static final String SERVICIO = "clientes";

    private final LeadCalificadoTraductor traductor;
    private final RegistroCalificacionService registro;
    private final ObjectMapper json;

    RegistrarCalificacionListener(LeadCalificadoTraductor traductor, RegistroCalificacionService registro,
            ObjectMapper json) {
        this.traductor = traductor;
        this.registro = registro;
        this.json = json;
    }

    @KafkaListener(topics = "${clientes.kafka.topicos.comandos}")
    void recibir(ConsumerRecord<String, byte[]> mensaje) {
        if (!esParaClientes(mensaje.value())) {
            log.debug("Comando de {} ignorado (no es para {}): partición {}, offset {}", mensaje.topic(), SERVICIO,
                    mensaje.partition(), mensaje.offset());
            return;
        }
        MDC.put(CorrelationIdFilter.MDC_KEY, UUID.randomUUID().toString());
        try {
            log.debug("Comando de {} recibido: partición {}, offset {}", mensaje.topic(), mensaje.partition(),
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

    /**
     * Si el comando es {@code RegistrarCalificacion} con {@code destino: clientes}. Lo ilegible (no es JSON o no es un
     * objeto) devuelve {@code true}: lo rechaza el traductor y va a la DLT, en vez de perderse en silencio.
     */
    private boolean esParaClientes(byte[] valor) {
        JsonNode raiz;
        try {
            raiz = valor == null ? null : json.readTree(valor);
        } catch (IOException e) {
            return true;
        }
        if (raiz == null || !raiz.isObject()) {
            return true;
        }
        if (!SERVICIO.equals(raiz.path("destino").asText(null))) {
            return false;
        }
        String tipo = raiz.path("eventType").asText(null);
        if (!LeadCalificadoTraductor.TIPO_EVENTO.equals(tipo)) {
            log.warn("Comando desconocido para {}: {}", SERVICIO, tipo);
            return false;
        }
        return true;
    }
}
