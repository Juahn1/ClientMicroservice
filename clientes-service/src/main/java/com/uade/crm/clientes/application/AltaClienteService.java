package com.uade.crm.clientes.application;

import com.uade.crm.clientes.domain.ClienteFactory;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.Coincidencia;
import com.uade.crm.clientes.domain.model.MotivoRevision;
import com.uade.crm.clientes.domain.model.ResultadoAlta;
import com.uade.crm.clientes.domain.model.SolicitudAlta;
import com.uade.crm.clientes.domain.port.ClaveDuplicadaException;
import com.uade.crm.clientes.domain.port.ClienteRepositorio;
import com.uade.crm.clientes.domain.valor.CuilProtegido;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Alta de cliente con deduplicación por CUIL y por email (design D4 del change 02 y D5 del change 04). No es
 * transaccional a propósito: cada intento corre en su propia transacción, porque la que choca contra un índice único
 * queda marcada para rollback y el reintento necesita una nueva.
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
     * Crea el cliente o, si ya hay uno activo con el mismo CUIL o el mismo email, registra un recontacto. Si otra
     * alta con la misma clave ganó la carrera del insert, se reintenta una vez: el reintento la encuentra y la trata
     * como recontacto.
     */
    public ResultadoAlta registrar(SolicitudAlta solicitud) {
        try {
            return transaccion.execute(estado -> intentar(solicitud));
        } catch (ClaveDuplicadaException carrera) {
            log.info("Alta simultánea con un {} ya registrado: se resuelve como recontacto", carrera.clave());
            return transaccion.execute(estado -> intentar(solicitud));
        }
    }

    /**
     * El CUIL identifica a la persona aunque cambie de email, así que se busca primero (design D5 del change 04).
     * Cada intento bloquea como máximo una fila; el cliente del email en el caso del posible duplicado solo se lee.
     */
    private ResultadoAlta intentar(SolicitudAlta solicitud) {
        Optional<CuilProtegido> cuil = solicitud.cuilInformado();
        if (cuil.isPresent()) {
            Optional<Cliente> porCuil = repositorio.buscarActivoPorCuilParaActualizar(cuil.get().hash());
            if (porCuil.isPresent()) {
                return recontactoPorCuil(porCuil.get(), solicitud);
            }
        }
        return repositorio.buscarActivoPorEmailParaActualizar(solicitud.email())
                .map(existente -> recontactoPorEmail(existente, solicitud))
                .orElseGet(() -> nuevo(solicitud));
    }

    /** El email informado no se guarda: el alta no pisa el registrado. Si es de otro cliente, posible duplicado. */
    private ResultadoAlta recontactoPorCuil(Cliente existente, SolicitudAlta solicitud) {
        Instant ahora = reloj.instant();
        existente.registrarRecontacto(solicitud, ahora);
        boolean emailDeOtro = repositorio.buscarActivoPorEmail(solicitud.email())
                .filter(otro -> !otro.id().equals(existente.id()))
                .isPresent();
        if (emailDeOtro && existente.marcarRevision(MotivoRevision.POSIBLE_DUPLICADO, ahora)) {
            log.info("Cliente {} marcado para revisión: su CUIL llegó con el email de otro cliente", existente.id());
        }
        return recontacto(existente, Coincidencia.CUIL);
    }

    /** Registra el CUIL si el cliente no tenía; si tenía otro, no lo toca y lo marca para revisión. */
    private ResultadoAlta recontactoPorEmail(Cliente existente, SolicitudAlta solicitud) {
        Instant ahora = reloj.instant();
        existente.registrarRecontacto(solicitud, ahora);
        solicitud.cuilInformado().ifPresent(cuil -> {
            if (existente.cuil().isEmpty()) {
                existente.registrarCuil(cuil, factory.versionTextoConsentimiento(), ahora);
            } else if (existente.marcarRevision(MotivoRevision.CUIL_DISTINTO, ahora)) {
                log.info("Cliente {} marcado para revisión: llegó otro CUIL con su email", existente.id());
            }
        });
        return recontacto(existente, Coincidencia.EMAIL);
    }

    private ResultadoAlta recontacto(Cliente existente, Coincidencia coincidencia) {
        repositorio.guardar(existente);
        log.info("Recontacto del cliente {} por {} ({} contactos)", existente.id(), coincidencia,
                existente.contactosRegistrados());
        return new ResultadoAlta(existente.id(), false, coincidencia);
    }

    private ResultadoAlta nuevo(SolicitudAlta solicitud) {
        Cliente cliente = repositorio.guardar(factory.crear(solicitud));
        log.info("Alta del cliente {}", cliente.id());
        return new ResultadoAlta(cliente.id(), true, Coincidencia.NINGUNA);
    }
}
