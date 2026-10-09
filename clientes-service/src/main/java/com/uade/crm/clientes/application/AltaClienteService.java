package com.uade.crm.clientes.application;

import com.uade.crm.clientes.domain.ClienteFactory;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.Coincidencia;
import com.uade.crm.clientes.domain.model.ResultadoAlta;
import com.uade.crm.clientes.domain.model.SolicitudAlta;
import com.uade.crm.clientes.domain.port.ClienteRepositorio;
import com.uade.crm.clientes.domain.port.EmailDuplicadoException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Alta de cliente con deduplicación por email (design D4). No es transaccional a propósito: cada intento corre en
 * su propia transacción, porque la que choca contra el índice único queda marcada para rollback y el reintento
 * necesita una nueva.
 */
@Service
public class AltaClienteService {

    private static final Logger log = LoggerFactory.getLogger(AltaClienteService.class);

    private final ClienteRepositorio repositorio;
    private final ClienteFactory factory;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    public AltaClienteService(ClienteRepositorio repositorio, ClienteFactory factory, TransactionTemplate transaccion,
            Clock reloj) {
        this.repositorio = repositorio;
        this.factory = factory;
        this.transaccion = transaccion;
        this.reloj = reloj;
    }

    /**
     * Crea el cliente o, si ya hay uno activo con el mismo email, registra un recontacto. Si otra alta con el mismo
     * email ganó la carrera del insert, se reintenta una vez: el reintento la encuentra y la trata como recontacto.
     */
    public ResultadoAlta registrar(SolicitudAlta solicitud) {
        try {
            return transaccion.execute(estado -> intentar(solicitud));
        } catch (EmailDuplicadoException carrera) {
            log.info("Alta simultánea con un email ya registrado: se resuelve como recontacto");
            return transaccion.execute(estado -> intentar(solicitud));
        }
    }

    private ResultadoAlta intentar(SolicitudAlta solicitud) {
        return repositorio.buscarActivoPorEmailParaActualizar(solicitud.email())
                .map(existente -> recontacto(existente, solicitud))
                .orElseGet(() -> nuevo(solicitud));
    }

    private ResultadoAlta recontacto(Cliente existente, SolicitudAlta solicitud) {
        existente.registrarRecontacto(solicitud, reloj.instant());
        repositorio.guardar(existente);
        log.info("Recontacto del cliente {} ({} contactos)", existente.id(), existente.contactosRegistrados());
        return new ResultadoAlta(existente.id(), false, Coincidencia.EMAIL);
    }

    private ResultadoAlta nuevo(SolicitudAlta solicitud) {
        Cliente cliente = repositorio.guardar(factory.crear(solicitud));
        log.info("Alta del cliente {}", cliente.id());
        return new ResultadoAlta(cliente.id(), true, Coincidencia.NINGUNA);
    }
}
