package com.uade.crm.clientes.domain.model;

import com.uade.crm.contracts.clientes.EstadoSituacionCrediticia;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Situación crediticia de un cliente (design D3 del change 05). La {@code situacion} (1 a 6) existe si y solo si el
 * estado es {@code CON_DATOS}; {@code periodo}, {@code fuente} y {@code consultadaEn} existen si y solo si la fuente
 * respondió ({@code CON_DATOS} o {@code SIN_REGISTROS}), y son los únicos estados que se guardan en la caché.
 */
public record SituacionCrediticia(
        EstadoSituacionCrediticia estado,
        Integer situacion,
        YearMonth periodo,
        FuenteSituacion fuente,
        Instant consultadaEn) {

    public SituacionCrediticia {
        Objects.requireNonNull(estado, "estado");
        boolean conDatos = estado == EstadoSituacionCrediticia.CON_DATOS;
        if (conDatos != (situacion != null)) {
            throw new IllegalArgumentException("La situación existe si y solo si el estado es CON_DATOS: " + estado);
        }
        if (situacion != null && (situacion < 1 || situacion > 6)) {
            throw new IllegalArgumentException("La situación debe estar entre 1 y 6 y es " + situacion);
        }
        boolean respondio = conDatos || estado == EstadoSituacionCrediticia.SIN_REGISTROS;
        if (respondio != (periodo != null) || respondio != (fuente != null) || respondio != (consultadaEn != null)) {
            throw new IllegalArgumentException(
                    "Período, fuente y fecha existen si y solo si la fuente respondió: " + estado);
        }
    }

    public static SituacionCrediticia conDatos(int situacion, YearMonth periodo, FuenteSituacion fuente,
            Instant consultadaEn) {
        return new SituacionCrediticia(EstadoSituacionCrediticia.CON_DATOS, situacion, periodo, fuente, consultadaEn);
    }

    public static SituacionCrediticia sinRegistros(YearMonth periodo, FuenteSituacion fuente, Instant consultadaEn) {
        return new SituacionCrediticia(EstadoSituacionCrediticia.SIN_REGISTROS, null, periodo, fuente, consultadaEn);
    }

    public static SituacionCrediticia sinCuil() {
        return new SituacionCrediticia(EstadoSituacionCrediticia.SIN_CUIL, null, null, null, null);
    }

    public static SituacionCrediticia noDisponible() {
        return new SituacionCrediticia(EstadoSituacionCrediticia.NO_DISPONIBLE, null, null, null, null);
    }

    /** Solo lo que informó la fuente se guarda: un {@code NO_DISPONIBLE} se vuelve a intentar en la próxima consulta. */
    public boolean esCacheable() {
        return consultadaEn != null;
    }
}
