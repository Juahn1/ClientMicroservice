package com.uade.crm.clientes.domain.port;

import com.uade.crm.clientes.domain.model.CalificacionHistorica;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Historial de calificaciones de los clientes (design D4 del change 08). Vive fuera del agregado {@code Cliente}: no
 * tiene invariantes con la ficha y crece con cada calificación.
 */
public interface HistorialCalificaciones {

    /**
     * Registra la calificación en la transacción en curso, solo si el cliente está activo y si no estaba registrada
     * (misma {@code leadId}, {@code origen} y {@code calculadaEn}). Una anonimización concurrente se ordena con el
     * registro: o la calificación no se inserta, o la anonimización la encuentra y la borra.
     *
     * @return true si la insertó; false si era un duplicado o el cliente no existe o no está activo
     */
    boolean registrar(CalificacionHistorica calificacion, Instant registradaEn);

    /** Calificaciones del cliente, de la calculada más recientemente a la más vieja. */
    List<CalificacionHistorica> delCliente(UUID clienteId);

    /** Borra todas las calificaciones del cliente en la transacción en curso. */
    void eliminarDelCliente(UUID clienteId);
}
