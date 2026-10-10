package com.uade.crm.clientes.infrastructure.precalificacion;

import com.uade.crm.clientes.domain.model.FuenteSituacion;
import com.uade.crm.clientes.domain.model.SituacionCrediticia;
import com.uade.crm.clientes.domain.port.ConsultaSituacion;
import com.uade.crm.clientes.domain.port.FuenteNoDisponibleException;
import com.uade.crm.clientes.domain.port.SituacionCrediticiaPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Fuente simulada y determinística de la Primera Parte (§5.10 del documento semilla, design D6 del change 05).
 * Resuelve la situación por el último dígito del DNI contenido en el CUIL: 0 a 4 → situación 1; 5 o 6 → 2; 7 → 3;
 * 8 → 5; 9 → sin registros. Informa como período el mes anterior al de la consulta.
 *
 * <p>Simula a la vez un servidor lento y un cliente con timeout: con una {@code demora} mayor que el timeout espera
 * solo el timeout y falla, como haría un cliente HTTP cuyo timeout de lectura vence (contrato de
 * {@link SituacionCrediticiaPort}). Con {@code forzarNoDisponible} falla de inmediato.
 */
public class SimuladaSituacionAdapter implements SituacionCrediticiaPort {

    private final Duration timeout;
    private final Duration demora;
    private final boolean forzarNoDisponible;
    private final Clock reloj;

    public SimuladaSituacionAdapter(Duration timeout, Duration demora, boolean forzarNoDisponible, Clock reloj) {
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.demora = Objects.requireNonNull(demora, "demora");
        this.forzarNoDisponible = forzarNoDisponible;
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    @Override
    public SituacionCrediticia consultar(ConsultaSituacion consulta) {
        if (forzarNoDisponible) {
            throw new FuenteNoDisponibleException("Fuente simulada configurada como no disponible");
        }
        esperar();
        Instant ahora = reloj.instant();
        YearMonth periodo = YearMonth.now(reloj).minusMonths(1);
        int ultimoDigitoDni = consulta.cuil().digitos().charAt(9) - '0';
        return switch (ultimoDigitoDni) {
            case 0, 1, 2, 3, 4 -> SituacionCrediticia.conDatos(1, periodo, FuenteSituacion.SIMULADA, ahora);
            case 5, 6 -> SituacionCrediticia.conDatos(2, periodo, FuenteSituacion.SIMULADA, ahora);
            case 7 -> SituacionCrediticia.conDatos(3, periodo, FuenteSituacion.SIMULADA, ahora);
            case 8 -> SituacionCrediticia.conDatos(5, periodo, FuenteSituacion.SIMULADA, ahora);
            default -> SituacionCrediticia.sinRegistros(periodo, FuenteSituacion.SIMULADA, ahora);
        };
    }

    private void esperar() {
        if (demora.isZero() || demora.isNegative()) {
            return;
        }
        boolean venceElTimeout = demora.compareTo(timeout) > 0;
        try {
            Thread.sleep(venceElTimeout ? timeout : demora);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FuenteNoDisponibleException("Consulta a la fuente simulada interrumpida", e);
        }
        if (venceElTimeout) {
            throw new FuenteNoDisponibleException("La fuente simulada no respondió dentro de " + timeout);
        }
    }
}
