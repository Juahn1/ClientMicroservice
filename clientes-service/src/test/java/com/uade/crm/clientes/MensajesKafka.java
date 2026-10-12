package com.uade.crm.clientes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/**
 * Lee los mensajes que publicó el servicio en el Kafka de prueba y publica los que consume (change 08). Los tests
 * comparten el broker, así que se filtra por clave ({@code clienteId} o {@code leadId}): cada test usa claves propias.
 */
@Component
public class MensajesKafka {

    private final String bootstrap;
    private KafkaProducer<String, String> productor;

    public MensajesKafka(KafkaConnectionDetails conexion) {
        this.bootstrap = String.join(",", conexion.getConsumerBootstrapServers());
    }

    /**
     * Publica un mensaje y espera la confirmación del broker. El valor viaja como UTF-8, igual que el JSON de
     * {@code calificacion}.
     */
    public synchronized void publicar(String topico, UUID clave, String valor) {
        if (productor == null) {
            productor = new KafkaProducer<>(Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
        }
        try {
            productor.send(new ProducerRecord<>(topico, clave.toString(), valor)).get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo publicar en " + topico, e);
        }
    }

    @PreDestroy
    synchronized void cerrar() {
        if (productor != null) {
            productor.close();
        }
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
