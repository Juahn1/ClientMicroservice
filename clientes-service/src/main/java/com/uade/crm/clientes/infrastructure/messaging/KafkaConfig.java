package com.uade.crm.clientes.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uade.crm.clientes.config.ClientesProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Tópicos propios del servicio (design D8 del change 03 y D5 del change 08): los que publica y el de mensajes fallidos
 * del consumidor. {@code KafkaAdmin} los crea al arrancar si no existen y, si el broker no está disponible, no impide
 * el arranque. {@code lead-calificado} no se crea: es de {@code calificacion}.
 */
@Configuration
class KafkaConfig {

    /**
     * El {@code JsonSerializer} usa por defecto su propio {@code ObjectMapper}, que escribe las fechas como números.
     * Con el de la aplicación, {@code occurredAt} sale en ISO-8601 como pide el sobre común; sin headers de tipo, los
     * consumidores de otros servicios no necesitan las clases Java de clientes.
     */
    @Bean
    @SuppressWarnings("unchecked")
    DefaultKafkaProducerFactoryCustomizer serializadorJson(ObjectMapper objectMapper) {
        return fabrica -> ((DefaultKafkaProducerFactory<Object, Object>) fabrica)
                .setValueSerializer(new JsonSerializer<>(objectMapper).noTypeInfo());
    }

    @Bean
    NewTopic topicoClienteActualizado(ClientesProperties properties) {
        return topico(properties.kafka().topicos().clienteActualizado(), properties.kafka());
    }

    @Bean
    NewTopic topicoClienteAnonimizado(ClientesProperties properties) {
        return topico(properties.kafka().topicos().clienteAnonimizado(), properties.kafka());
    }

    /** Mensajes de {@code lead-calificado} que no se pudieron registrar (design D5 del change 08). */
    @Bean
    NewTopic topicoLeadCalificadoFallidos(ClientesProperties properties) {
        return topico(properties.kafka().topicos().leadCalificadoDlt(), properties.kafka());
    }

    private static NewTopic topico(String nombre, ClientesProperties.Kafka kafka) {
        return TopicBuilder.name(nombre).partitions(kafka.particiones()).replicas(kafka.replicas()).build();
    }
}
