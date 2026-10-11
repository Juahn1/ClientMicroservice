package com.uade.crm.clientes.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uade.crm.clientes.domain.model.CalificacionHistorica;
import com.uade.crm.clientes.domain.port.HistorialCalificaciones;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador del puerto {@link HistorialCalificaciones} sobre la tabla {@code historial_calificacion} (design D4). */
@Repository
class HistorialCalificacionesJpa implements HistorialCalificaciones {

    /** Solo serializa una lista de textos: no necesita la configuración del {@code ObjectMapper} de la aplicación. */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HistorialCalificacionJpaRepository jpa;

    HistorialCalificacionesJpa(HistorialCalificacionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public boolean registrar(CalificacionHistorica c, Instant registradaEn) {
        return jpa.registrar(UUID.randomUUID(), c.clienteId(), c.leadId(), c.nivel(), c.puntaje(), c.origen(),
                c.calculadaEn(), c.observacion(), json(c.motivos()), registradaEn) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CalificacionHistorica> delCliente(UUID clienteId) {
        return jpa.findByClienteIdOrderByCalculadaEnDescRegistradaEnDescIdAsc(clienteId).stream()
                .map(HistorialCalificacionesJpa::aDominio)
                .toList();
    }

    @Override
    @Transactional
    public void eliminarDelCliente(UUID clienteId) {
        jpa.eliminarDelCliente(clienteId);
    }

    private static String json(List<String> motivos) {
        try {
            return JSON.writeValueAsString(motivos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudieron serializar los motivos", e);
        }
    }

    private static CalificacionHistorica aDominio(HistorialCalificacionEntity e) {
        return new CalificacionHistorica(e.getLeadId(), e.getClienteId(), e.getNivel(), e.getPuntaje(), e.getOrigen(),
                e.getCalculadaEn(), e.getObservacion(), e.getMotivos());
    }
}
