package com.uade.crm.clientes;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Kafka real para los tests de integración, con la misma imagen que {@code infra/docker-compose.yml}. Como bean,
 * Spring reutiliza el contenedor entre las clases de test que comparten contexto.
 */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaDePrueba {

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:3.9.1");
    }
}
