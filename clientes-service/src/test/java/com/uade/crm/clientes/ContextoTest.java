package com.uade.crm.clientes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uade.crm.clientes.config.ClientesProperties;
import java.sql.Connection;
import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/** Arranque completo contra PostgreSQL 16 y Kafka reales; también cubre "Documentación de la API". */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ContextoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("clientes_db");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    @Autowired
    MockMvc mvc;

    @Autowired
    ClientesProperties properties;

    @Autowired
    Flyway flyway;

    @Test
    void cargaLaConfiguracionDeLaSeccion59() {
        assertThat(properties.paginacion().tamanioMaximo()).isEqualTo(50);
        assertThat(properties.paginacion().tamanioPorDefecto()).isEqualTo(20);
        assertThat(properties.situacionCrediticia().modo())
                .isEqualTo(ClientesProperties.ModoSituacionCrediticia.SIMULADO);
        assertThat(properties.situacionCrediticia().vigencia()).isEqualTo(Duration.ofDays(30));
        assertThat(properties.kafka().topicos().clienteActualizado()).isEqualTo("cliente-actualizado");
        assertThat(properties.cuil().claveCifrado()).isNotBlank();
    }

    @Test
    void flywayApuntaAClientesDb() throws Exception {
        try (Connection conexion = flyway.getConfiguration().getDataSource().getConnection()) {
            assertThat(conexion.getCatalog()).isEqualTo("clientes_db");
        }
        assertThat(flyway.info().applied())
                .extracting(migracion -> migracion.getScript())
                .containsExactly("V1__cliente.sql");
    }

    @Test
    void laMigracionCreaLaTablaClienteConSusIndices() throws Exception {
        try (Connection conexion = flyway.getConfiguration().getDataSource().getConnection();
                var consulta = conexion.createStatement().executeQuery(
                        "SELECT indexname FROM pg_indexes WHERE tablename = 'cliente' ORDER BY indexname")) {
            var indices = new java.util.ArrayList<String>();
            while (consulta.next()) {
                indices.add(consulta.getString(1));
            }
            assertThat(indices).contains("ux_cliente_email_activo", "ix_cliente_telefono_activo",
                    "ix_cliente_provincia", "ix_cliente_ultimo_contacto");
        }
    }

    @Test
    void contratoOpenApiDisponible() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.")))
                .andExpect(jsonPath("$.info.title").value("Servicio de Clientes"));
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void endpointDeSalud() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
