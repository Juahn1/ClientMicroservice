package com.uade.crm.clientes.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Consentimiento del titular para una finalidad, con la versión del texto aceptado (design D3 del change 04). Es
 * parte del agregado {@link Cliente}: solo él lo crea y lo revoca. El id se genera en el dominio para que guardar
 * dos veces el mismo cliente actualice la fila en lugar de duplicarla.
 */
public final class Consentimiento {

    private final UUID id;
    private final FinalidadConsentimiento finalidad;
    private final String versionTexto;
    private final OrigenConsentimiento origen;
    private final Instant otorgadoEn;
    private Instant revocadoEn;

    public Consentimiento(UUID id, FinalidadConsentimiento finalidad, String versionTexto,
            OrigenConsentimiento origen, Instant otorgadoEn, Instant revocadoEn) {
        this.id = Objects.requireNonNull(id, "id");
        this.finalidad = Objects.requireNonNull(finalidad, "finalidad");
        this.versionTexto = Objects.requireNonNull(versionTexto, "versionTexto");
        this.origen = Objects.requireNonNull(origen, "origen");
        this.otorgadoEn = Objects.requireNonNull(otorgadoEn, "otorgadoEn");
        this.revocadoEn = revocadoEn;
    }

    static Consentimiento otorgar(FinalidadConsentimiento finalidad, String versionTexto,
            OrigenConsentimiento origen, Instant ahora) {
        return new Consentimiento(UUID.randomUUID(), finalidad, versionTexto, origen, ahora, null);
    }

    void revocar(Instant ahora) {
        if (revocadoEn == null) {
            revocadoEn = ahora;
        }
    }

    public boolean vigente() {
        return revocadoEn == null;
    }

    public UUID id() {
        return id;
    }

    public FinalidadConsentimiento finalidad() {
        return finalidad;
    }

    public String versionTexto() {
        return versionTexto;
    }

    public OrigenConsentimiento origen() {
        return origen;
    }

    public Instant otorgadoEn() {
        return otorgadoEn;
    }

    public Optional<Instant> revocadoEn() {
        return Optional.ofNullable(revocadoEn);
    }

    @Override
    public String toString() {
        return "Consentimiento[" + finalidad + ", vigente=" + vigente() + "]";
    }
}
