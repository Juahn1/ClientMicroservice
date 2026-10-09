package com.uade.crm.clientes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.stereotype.Component;

/**
 * Lee los mensajes que publicó el servicio en el Kafka de prueba. Los tests comparten el broker, así que se filtra
 * por clave ({@code clienteId}): cada test usa clientes propios.
 */
@Component
public class MensajesKafka {

    private final String bootstrap;

    public MensajesKafka(KafkaConnectionDetails conexion) {
        this.bootstrap = String.join(",", conexion.getConsumerBootstrapServers());
    }

    /** Espera hasta {@code esperados} mensajes con la clave dada o hasta 15 segundos. */
    public List<ConsumerRecord<String, String>> esperar(String topico, UUID clave, int esperados) {
        return leer(topico, clave, esperados, Duration.ofSeconds(15));
    }

    /** Lee durante unos segundos y devuelve todo lo que haya con esa clave; sirve para verificar ausencia. */
    public List<ConsumerRecord<String, String>> ninguno(String topico, UUID clave) {
        return leer(topico, clave, Integer.MAX_VALUE, Duration.ofSeconds(4));
    }

    private List<ConsumerRecord<String, String>> leer(String topico, UUID clave, int esperados, Duration espera) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> encontrados = new ArrayList<>();
        try (KafkaConsumer<String, String> consumidor = new KafkaConsumer<>(config)) {
            consumidor.subscribe(List.of(topico));
            Instant limite = Instant.now().plus(espera);
            while (Instant.now().isBefore(limite) && encontrados.size() < esperados) {
                for (ConsumerRecord<String, String> mensaje : consumidor.poll(Duration.ofMillis(250))) {
                    if (clave.toString().equals(mensaje.key())) {
                        encontrados.add(mensaje);
                    }
                }
            }
        }
        return encontrados;
    }
}
