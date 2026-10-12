package com.uade.crm.clientes.infrastructure.persistence;

import com.uade.crm.clientes.domain.model.FuenteSituacion;
import com.uade.crm.contracts.clientes.EstadoSituacionCrediticia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de la tabla {@code situacion_crediticia} ({@code V4__situacion_crediticia.sql}). Solo se lee con JPA: la
 * escritura es un upsert nativo de {@link SituacionCrediticiaJpaRepository} (design D4 del change 05).
 */
@Entity
@Table(name = "situacion_crediticia")
public class SituacionCrediticiaEntity {

    @Id
    @Column(name = "cliente_id")
    private UUID clienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSituacionCrediticia estado;

    private Short situacion;

    @Column(nullable = false, length = 6)
    private String periodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FuenteSituacion fuente;

    @Column(name = "consultada_en", nullable = false)
    private Instant consultadaEn;

    @Column(name = "vigente_hasta", nullable = false)
    private Instant vigenteHasta;

    protected SituacionCrediticiaEntity() {
    }

    UUID getClienteId() {
        return clienteId;
    }

    EstadoSituacionCrediticia getEstado() {
        return estado;
    }

    Short getSituacion() {
        return situacion;
    }

    String getPeriodo() {
        return periodo;
    }

    FuenteSituacion getFuente() {
        return fuente;
    }

    Instant getConsultadaEn() {
        return consultadaEn;
    }

    Instant getVigenteHasta() {
        return vigenteHasta;
    }
}
