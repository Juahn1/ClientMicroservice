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
 * Los tres tópicos del ecosistema (docs/08-contrato-tres-topicos.md del orquestador). {@code KafkaAdmin} los crea al
 * arrancar si no existen (el orquestador también) y, si el broker no está disponible, no impide el arranque.
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

    /** Las órdenes del orquestador a todos los servicios. */
    @Bean
    NewTopic topicoComandos(ClientesProperties properties) {
        return topico(properties.kafka().topicos().comandos(), properties.kafka());
    }

    /** Los hechos que publican los servicios: acá van {@code ClienteActualizado} y {@code ClienteAnonimizado}. */
    @Bean
    NewTopic topicoEventos(ClientesProperties properties) {
        return topico(properties.kafka().topicos().eventos(), properties.kafka());
    }

    /** Los comandos que no se pudieron procesar (design D5 del change 08). */
    @Bean
    NewTopic topicoFallidos(ClientesProperties properties) {
        return topico(properties.kafka().topicos().dlt(), properties.kafka());
    }

    private static NewTopic topico(String nombre, ClientesProperties.Kafka kafka) {
        return TopicBuilder.name(nombre).partitions(kafka.particiones()).replicas(kafka.replicas()).build();
    }
}
