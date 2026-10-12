package com.uade.crm.clientes;

import com.uade.crm.clientes.config.ClientesProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Kafka real para los tests de integración, con la misma imagen que {@code infra/docker-compose.yml}. Como bean,
 * Spring reutiliza el contenedor entre las clases de test que comparten contexto.
 */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaDePrueba {

    /**
     * {@code lead-calificado} es de {@code calificacion}: el servicio no lo crea (design D7 del change 08), así que en
     * los tests lo crea esta configuración, con tres particiones como los tópicos propios.
     */
    @Bean
    NewTopic topicoLeadCalificado(ClientesProperties properties) {
        return TopicBuilder.name(properties.kafka().topicos().leadCalificado()).partitions(3).replicas(1).build();
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:3.9.1");
    }
}
