package com.uade.crm.clientes.application;

import com.uade.crm.clientes.domain.model.CambiosCliente;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.ClienteAnonimizadoException;
import com.uade.crm.clientes.domain.model.ClienteNoEncontradoException;
import com.uade.crm.clientes.domain.port.ClienteRepositorio;
import com.uade.crm.clientes.domain.port.EmailDuplicadoException;
import com.uade.crm.contracts.clientes.CampoCliente;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edición de un cliente por parte del personal, con control de concurrencia optimista (design D4 del change 03).
 * El {@code If-Match} se compara con la versión leída; si otra escritura confirma entre la lectura y el guardado,
 * {@code @Version} lo detecta y la respuesta es la misma (412).
 */
@Service
public class EdicionClienteService {

    private static final Logger log = LoggerFactory.getLogger(EdicionClienteService.class);

    private final ClienteRepositorio repositorio;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public EdicionClienteService(ClienteRepositorio repositorio, ApplicationEventPublisher eventos, Clock reloj) {
        this.repositorio = repositorio;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /** Devuelve el cliente con la versión vigente: la nueva si hubo cambios, la misma si no. */
    @Transactional
    public Cliente editar(UUID id, long versionEsperada, CambiosCliente cambios) {
        Cliente cliente = repositorio.buscarPorId(id).orElseThrow(() -> new ClienteNoEncontradoException(id));
        if (!cliente.activo()) {
            throw new ClienteAnonimizadoException(id);
        }
        if (cliente.version().orElse(0L) != versionEsperada) {
            throw new VersionDesactualizadaException(id);
        }
        exigirEmailLibre(cliente, cambios);
        Set<CampoCliente> campos = cliente.editar(cambios, reloj.instant());
        if (campos.isEmpty()) {
            return cliente;
        }
        Cliente guardado = guardar(cliente);
        eventos.publishEvent(EventosCliente.actualizado(id, campos, cliente.actualizadoEn()));
        log.info("Edición del cliente {}: {}", id, campos);
        return guardado;
    }

    /** Respuesta clara antes de escribir; el índice único sigue siendo la garantía ante una carrera. */
    private void exigirEmailLibre(Cliente cliente, CambiosCliente cambios) {
        if (cambios.email() == null || cambios.email().equals(cliente.email())) {
            return;
        }
        repositorio.buscarActivoPorEmail(cambios.email())
                .filter(otro -> !otro.id().equals(cliente.id()))
                .ifPresent(otro -> {
                    throw new EmailEnUsoException(cliente.id());
                });
    }

    private Cliente guardar(Cliente cliente) {
        try {
            return repositorio.guardar(cliente);
        } catch (EmailDuplicadoException carrera) {
            throw new EmailEnUsoException(cliente.id());
        } catch (OptimisticLockingFailureException carrera) {
            throw new VersionDesactualizadaException(cliente.id());
        }
    }
}
