package com.uade.crm.clientes.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Una calificación de un lead del cliente, tal como la publicó {@code calificacion} (design D4 del change 08). El
 * servicio no interpreta el puntaje: {@code nivel} y {@code origen} son texto porque su vocabulario es de
 * {@code calificacion}, y un valor nuevo no tiene que impedir registrarla.
 *
 * <p>{@code motivos} y {@code observacion} describen la situación financiera de la persona: {@link #toString()} los
 * omite para que no terminen en un log.
 *
 * @param calculadaEn cuándo la calculó o corrigió {@code calificacion}, con la zona ya resuelta
 * @param observacion detalle de la calificación o motivo de la corrección; puede ser {@code null}
 * @param motivos     desglose de cada regla, en el orden en que llegó; nunca {@code null}
 */
public record CalificacionHistorica(
        UUID leadId,
        UUID clienteId,
        String nivel,
        int puntaje,
        String origen,
        Instant calculadaEn,
        String observacion,
        List<String> motivos) {

    public CalificacionHistorica {
        Objects.requireNonNull(leadId, "leadId");
        Objects.requireNonNull(clienteId, "clienteId");
        Objects.requireNonNull(nivel, "nivel");
        Objects.requireNonNull(origen, "origen");
        Objects.requireNonNull(calculadaEn, "calculadaEn");
        motivos = motivos == null ? List.of() : List.copyOf(motivos);
    }

    @Override
    public String toString() {
        return "CalificacionHistorica[leadId=" + leadId + ", clienteId=" + clienteId + ", nivel=" + nivel
                + ", puntaje=" + puntaje + ", origen=" + origen + ", calculadaEn=" + calculadaEn + "]";
    }
}
