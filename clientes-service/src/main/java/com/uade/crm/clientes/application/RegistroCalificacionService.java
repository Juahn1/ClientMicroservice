package com.uade.crm.clientes.application;

import com.uade.crm.clientes.domain.model.CalificacionHistorica;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.port.ClienteRepositorio;
import com.uade.crm.clientes.domain.port.HistorialCalificaciones;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra en el historial las calificaciones que publica {@code calificacion} (design D6 del change 08). Solo
 * registra calificaciones de clientes activos y una sola vez cada una; los descartes no son errores: el consumidor
 * sigue con el próximo mensaje.
 *
 * <p>Los logs identifican la calificación por {@code leadId} y {@code clienteId}, nunca con sus motivos ni su
 * observación.
 */
@Service
public class RegistroCalificacionService {

    private static final Logger log = LoggerFactory.getLogger(RegistroCalificacionService.class);

    /** Qué pasó con una calificación recibida. */
    public enum Resultado {
        REGISTRADA, DUPLICADA, CLIENTE_ANONIMIZADO, CLIENTE_INEXISTENTE
    }

    private final HistorialCalificaciones historial;
    private final ClienteRepositorio clientes;
    private final Clock reloj;

    public RegistroCalificacionService(HistorialCalificaciones historial, ClienteRepositorio clientes, Clock reloj) {
        this.historial = historial;
        this.clientes = clientes;
        this.reloj = reloj;
    }

    /**
     * Una sola sentencia decide si se registra. Solo cuando no se registró se consulta al cliente, y únicamente para
     * dejar en el log el motivo del descarte con su nivel.
     */
    @Transactional
    public Resultado registrar(CalificacionHistorica calificacion) {
        if (historial.registrar(calificacion, reloj.instant())) {
            log.info("Calificación registrada: lead {}, cliente {}, origen {}", calificacion.leadId(),
                    calificacion.clienteId(), calificacion.origen());
            return Resultado.REGISTRADA;
        }
        Optional<Cliente> cliente = clientes.buscarPorId(calificacion.clienteId());
        if (cliente.isEmpty()) {
            log.warn("Calificación descartada: el cliente {} no existe (lead {})", calificacion.clienteId(),
                    calificacion.leadId());
            return Resultado.CLIENTE_INEXISTENTE;
        }
        if (!cliente.get().activo()) {
            log.info("Calificación descartada: el cliente {} está anonimizado (lead {})", calificacion.clienteId(),
                    calificacion.leadId());
            return Resultado.CLIENTE_ANONIMIZADO;
        }
        log.debug("Calificación descartada: ya estaba registrada (lead {}, cliente {}, origen {})",
                calificacion.leadId(), calificacion.clienteId(), calificacion.origen());
        return Resultado.DUPLICADA;
    }
}
