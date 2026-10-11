package com.uade.crm.clientes.application;

import com.uade.crm.clientes.domain.model.CalificacionHistorica;
import com.uade.crm.clientes.domain.port.HistorialCalificaciones;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta del historial de calificaciones de un cliente (design D8 del change 08). */
@Service
@Transactional(readOnly = true)
public class HistorialCalificacionesService {

    private final ConsultaClienteService clientes;
    private final HistorialCalificaciones historial;

    public HistorialCalificacionesService(ConsultaClienteService clientes, HistorialCalificaciones historial) {
        this.clientes = clientes;
        this.historial = historial;
    }

    /** Las calificaciones del cliente, la más reciente primero. Inexistente responde 404; anonimizado, 410. */
    public List<CalificacionHistorica> consultar(UUID clienteId) {
        clientes.obtener(clienteId);
        return historial.delCliente(clienteId);
    }
}
