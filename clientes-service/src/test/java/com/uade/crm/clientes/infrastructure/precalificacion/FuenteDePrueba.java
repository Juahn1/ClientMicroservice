package com.uade.crm.clientes.infrastructure.precalificacion;

import com.uade.crm.clientes.domain.model.SituacionCrediticia;
import com.uade.crm.clientes.domain.port.ConsultaSituacion;
import com.uade.crm.clientes.domain.port.SituacionCrediticiaPort;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fuente controlable para tests (design D10 del change 05): cuenta las llamadas y permite forzar la caída, una demora
 * o una excepción inesperada. Delega en un {@link SimuladaSituacionAdapter}, así que respeta el timeout como cualquier
 * fuente.
 */
public class FuenteDePrueba implements SituacionCrediticiaPort {

    private final Duration timeout;
    private final Clock reloj;
    private final AtomicInteger llamadas = new AtomicInteger();
    private volatile Duration demora = Duration.ZERO;
    private volatile boolean caida;
    private volatile RuntimeException inesperada;

    public FuenteDePrueba(Duration timeout, Clock reloj) {
        this.timeout = timeout;
        this.reloj = reloj;
    }

    @Override
    public SituacionCrediticia consultar(ConsultaSituacion consulta) {
        llamadas.incrementAndGet();
        if (inesperada != null) {
            throw inesperada;
        }
        return new SimuladaSituacionAdapter(timeout, demora, caida, reloj).consultar(consulta);
    }

    public int llamadas() {
        return llamadas.get();
    }

    public void caer() {
        caida = true;
    }

    public void demorar(Duration demora) {
        this.demora = demora;
    }

    public void fallarCon(RuntimeException inesperada) {
        this.inesperada = inesperada;
    }

    /** Vuelve a la fuente normal y pone el contador en cero. */
    public void restablecer() {
        caida = false;
        demora = Duration.ZERO;
        inesperada = null;
        llamadas.set(0);
    }
}
