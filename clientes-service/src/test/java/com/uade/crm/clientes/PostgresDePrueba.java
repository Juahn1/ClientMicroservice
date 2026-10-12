package com.uade.crm.clientes;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQL 16 real para los tests de integración. Como bean, Spring reutiliza el contenedor entre las clases de
 * test que comparten configuración (caché de contextos), en lugar de levantar uno por clase.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresDePrueba {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("clientes_db");
    }
}
