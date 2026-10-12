package com.uade.crm.clientes.application.listener;

import com.uade.crm.clientes.config.ClientesProperties;
import com.uade.crm.clientes.domain.event.ClienteActualizadoEvento;
import com.uade.crm.clientes.domain.event.ClienteAnonimizadoEvento;
import com.uade.crm.clientes.domain.port.PublicadorEventos;
import com.uade.crm.contracts.EventoIntegracion;
import com.uade.crm.contracts.clientes.ClienteActualizado;
import com.uade.crm.contracts.clientes.ClienteAnonimizado;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Traduce los eventos de dominio a eventos de integración y los publica después del commit (patrón Observer,
 * design D8 del change 03). Si la transacción se revierte, no se ejecuta. Ninguna falla de la publicación sale de
 * acá: la operación ya está confirmada y su respuesta no puede cambiar.
 */
@Component
public class ClienteEventosListener {

    private static final Logger log = LoggerFactory.getLogger(ClienteEventosListener.class);

    private final PublicadorEventos publicador;
    private final ClientesProperties.Kafka.Topicos topicos;

    public ClienteEventosListener(PublicadorEventos publicador, ClientesProperties properties) {
        this.publicador = publicador;
        this.topicos = properties.kafka().topicos();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void clienteActualizado(ClienteActualizadoEvento evento) {
        var payload = new ClienteActualizado(evento.clienteId(), evento.campos().stream().sorted().toList());
        publicar(topicos.clienteActualizado(), evento.clienteId(), new EventoIntegracion<>(UUID.randomUUID(),
                ClienteActualizado.TIPO, ClienteActualizado.VERSION, evento.ocurridoEn(), evento.correlationId(),
                payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void clienteAnonimizado(ClienteAnonimizadoEvento evento) {
        publicar(topicos.clienteAnonimizado(), evento.clienteId(), new EventoIntegracion<>(UUID.randomUUID(),
                ClienteAnonimizado.TIPO, ClienteAnonimizado.VERSION, evento.ocurridoEn(), evento.correlationId(),
                new ClienteAnonimizado(evento.clienteId())));
    }

    private void publicar(String topico, UUID clienteId, EventoIntegracion<?> evento) {
        try {
            publicador.publicar(topico, clienteId, evento);
        } catch (RuntimeException falla) {
            log.error("No se pudo publicar {} del cliente {}", evento.eventType(), clienteId, falla);
        }
    }
}
