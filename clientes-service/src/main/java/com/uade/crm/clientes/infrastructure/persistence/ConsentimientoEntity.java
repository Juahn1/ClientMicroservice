package com.uade.crm.clientes.infrastructure.persistence;

import com.uade.crm.clientes.domain.model.FinalidadConsentimiento;
import com.uade.crm.clientes.domain.model.OrigenConsentimiento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de la tabla {@code consentimiento} ({@code V3__cuil_y_consentimiento.sql}). La columna {@code cliente_id} la
 * maneja la colección de {@link ClienteEntity} (dueña de la relación), así que acá no se mapea.
 */
@Entity
@Table(name = "consentimiento")
public class ConsentimientoEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FinalidadConsentimiento finalidad;

    @Column(name = "version_texto", nullable = false, length = 20)
    private String versionTexto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenConsentimiento origen;

    @Column(name = "otorgado_en", nullable = false)
    private Instant otorgadoEn;

    @Column(name = "revocado_en")
    private Instant revocadoEn;

    protected ConsentimientoEntity() {
    }

    ConsentimientoEntity(UUID id, FinalidadConsentimiento finalidad, String versionTexto,
            OrigenConsentimiento origen, Instant otorgadoEn, Instant revocadoEn) {
        this.id = id;
        this.finalidad = finalidad;
        this.versionTexto = versionTexto;
        this.origen = origen;
        this.otorgadoEn = otorgadoEn;
        this.revocadoEn = revocadoEn;
    }

    UUID getId() {
        return id;
    }

    FinalidadConsentimiento getFinalidad() {
        return finalidad;
    }

    String getVersionTexto() {
        return versionTexto;
    }

    OrigenConsentimiento getOrigen() {
        return origen;
    }

    Instant getOtorgadoEn() {
        return otorgadoEn;
    }

    Instant getRevocadoEn() {
        return revocadoEn;
    }
}
