package com.uade.crm.clientes.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Fila de la tabla {@code historial_calificacion} ({@code V5__historial_calificacion.sql}). Solo se lee con JPA: la
 * escritura es el {@code INSERT ... SELECT} nativo de {@link HistorialCalificacionJpaRepository} (design D4 del change
 * 08).
 */
@Entity
@Table(name = "historial_calificacion")
public class HistorialCalificacionEntity {

    @Id
    private UUID id;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(nullable = false, length = 40)
    private String nivel;

    @Column(nullable = false)
    private int puntaje;

    @Column(nullable = false, length = 40)
    private String origen;

    @Column(name = "calculada_en", nullable = false)
    private Instant calculadaEn;

    @Column(length = 2000)
    private String observacion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> motivos;

    @Column(name = "registrada_en", nullable = false)
    private Instant registradaEn;

    protected HistorialCalificacionEntity() {
    }

    UUID getClienteId() {
        return clienteId;
    }

    UUID getLeadId() {
        return leadId;
    }

    String getNivel() {
        return nivel;
    }

    int getPuntaje() {
        return puntaje;
    }

    String getOrigen() {
        return origen;
    }

    Instant getCalculadaEn() {
        return calculadaEn;
    }

    String getObservacion() {
        return observacion;
    }

    List<String> getMotivos() {
        return motivos;
    }
}
