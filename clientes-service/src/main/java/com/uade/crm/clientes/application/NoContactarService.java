package com.uade.crm.clientes.application;

import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.ClienteAnonimizadoException;
import com.uade.crm.clientes.domain.model.ClienteNoEncontradoException;
import com.uade.crm.clientes.domain.port.ClienteRepositorio;
import com.uade.crm.contracts.clientes.CampoCliente;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marca no contactar (design D5 del change 03). Sin {@code If-Match}: lee el cliente bloqueando la fila, así se
 * encola detrás de un recontacto o de otra marca en lugar de fallar por control optimista. Las dos operaciones son
 * idempotentes; solo un cambio real guarda y publica {@code ClienteActualizado} con {@code NO_CONTACTAR}.
 */
@Service
public class NoContactarService {

    private static final Logger log = LoggerFactory.getLogger(NoContactarService.class);

    private final ClienteRepositorio repositorio;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public NoContactarService(ClienteRepositorio repositorio, ApplicationEventPublisher eventos, Clock reloj) {
        this.repositorio = repositorio;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    @Transactional
    public void marcar(UUID id) {
        Cliente cliente = activoParaActualizar(id);
        Instant ahora = reloj.instant();
        if (cliente.marcarNoContactar(ahora)) {
            guardarYPublicar(cliente, ahora);
            log.info("Cliente {} marcado como no contactar", id);
        }
    }

    @Transactional
    public void quitar(UUID id) {
        Cliente cliente = activoParaActualizar(id);
        Instant ahora = reloj.instant();
        if (cliente.quitarNoContactar(ahora)) {
            guardarYPublicar(cliente, ahora);
            log.info("Cliente {} desmarcado como no contactar", id);
        }
    }

    private Cliente activoParaActualizar(UUID id) {
        Cliente cliente = repositorio.buscarPorIdParaActualizar(id)
                .orElseThrow(() -> new ClienteNoEncontradoException(id));
        if (!cliente.activo()) {
            throw new ClienteAnonimizadoException(id);
        }
        return cliente;
    }

    private void guardarYPublicar(Cliente cliente, Instant ahora) {
        repositorio.guardar(cliente);
        eventos.publishEvent(EventosCliente.actualizado(cliente.id(), Set.of(CampoCliente.NO_CONTACTAR), ahora));
    }
}
