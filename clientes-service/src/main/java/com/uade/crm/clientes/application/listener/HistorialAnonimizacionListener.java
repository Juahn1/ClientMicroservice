package com.uade.crm.clientes.application.listener;

import com.uade.crm.clientes.domain.event.ClienteAnonimizadoEvento;
import com.uade.crm.clientes.domain.port.HistorialCalificaciones;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Borra el historial de calificaciones de un cliente que se anonimiza, en la misma transacción (design D6 del change
 * 08, igual que {@link SituacionCrediticiaAnonimizacionListener}). Los motivos describen la situación financiera de la
 * persona; el historial es una copia de lo que guarda {@code calificacion}, así que borrarlo no pierde información del
 * sistema. Si el borrado falla, la anonimización completa se revierte.
 */
@Component
public class HistorialAnonimizacionListener {

    private final HistorialCalificaciones historial;

    public HistorialAnonimizacionListener(HistorialCalificaciones historial) {
        this.historial = historial;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void clienteAnonimizado(ClienteAnonimizadoEvento evento) {
        historial.eliminarDelCliente(evento.clienteId());
    }
}
