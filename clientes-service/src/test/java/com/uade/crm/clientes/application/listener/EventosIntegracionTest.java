package com.uade.crm.clientes.application.listener;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uade.crm.clientes.MensajesKafka;
import com.uade.crm.clientes.PruebaIntegracion;
import com.uade.crm.clientes.domain.event.ClienteActualizadoEvento;
import com.uade.crm.clientes.domain.event.ClienteAnonimizadoEvento;
import com.uade.crm.contracts.clientes.CampoCliente;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/** Requirements de clientes-eventos: publicación después del commit, sobre común y clave por cliente. */
@PruebaIntegracion
@Import(MensajesKafka.class)
class EventosIntegracionTest {

    private static final Instant AHORA = Instant.parse("2026-10-08T12:00:00Z");

    @Autowired
    ApplicationEventPublisher eventos;

    @Autowired
    TransactionTemplate transaccion;

    @Autowired
    MensajesKafka mensajes;

    @Autowired
    ObjectMapper json;

    @Test
    void clienteActualizadoLlegaAlTopicoConElSobreCompleto() throws Exception {
        UUID id = UUID.randomUUID();

        transaccion.executeWithoutResult(estado -> eventos.publishEvent(new ClienteActualizadoEvento(
                id, Set.of(CampoCliente.EMAIL, CampoCliente.NOMBRE), AHORA, "abc-123")));

        ConsumerRecord<String, String> mensaje = mensajes.esperar("cliente-actualizado", id, 1).getFirst();
        JsonNode sobre = json.readTree(mensaje.value());
        assertThat(mensaje.key()).isEqualTo(id.toString());
        assertThat(mensaje.headers().lastHeader("__TypeId__")).isNull();
        assertThat(UUID.fromString(sobre.get("eventId").asText())).isNotNull();
        assertThat(sobre.get("eventType").asText()).isEqualTo("ClienteActualizado");
        assertThat(sobre.get("version").asInt()).isEqualTo(1);
        assertThat(Instant.parse(sobre.get("occurredAt").asText())).isEqualTo(AHORA);
        assertThat(sobre.get("correlationId").asText()).isEqualTo("abc-123");
        assertThat(sobre.get("payload").get("clienteId").asText()).isEqualTo(id.toString());
        assertThat(sobre.get("payload").get("campos")).extracting(JsonNode::asText)
                .containsExactly("NOMBRE", "EMAIL");
        assertThat(sobre.get("payload").size()).isEqualTo(2);
    }

    @Test
    void clienteAnonimizadoLlevaSoloElId() throws Exception {
        UUID id = UUID.randomUUID();

        transaccion.executeWithoutResult(estado ->
                eventos.publishEvent(new ClienteAnonimizadoEvento(id, AHORA, "abc-456")));

        JsonNode sobre = json.readTree(mensajes.esperar("cliente-anonimizado", id, 1).getFirst().value());
        assertThat(sobre.get("eventType").asText()).isEqualTo("ClienteAnonimizado");
        assertThat(sobre.get("correlationId").asText()).isEqualTo("abc-456");
        assertThat(sobre.get("payload").get("clienteId").asText()).isEqualTo(id.toString());
        assertThat(sobre.get("payload").size()).isEqualTo(1);
    }

    @Test
    void unRollbackNoPublicaNada() {
        UUID id = UUID.randomUUID();

        transaccion.executeWithoutResult(estado -> {
            eventos.publishEvent(new ClienteActualizadoEvento(id, Set.of(CampoCliente.NOMBRE), AHORA, null));
            estado.setRollbackOnly();
        });

        assertThat(mensajes.ninguno("cliente-actualizado", id)).isEmpty();
    }
}
