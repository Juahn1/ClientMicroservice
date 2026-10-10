package com.uade.crm.clientes.infrastructure.precalificacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.uade.crm.clientes.domain.port.ConsultaSituacion;
import com.uade.crm.clientes.domain.valor.Cuil;
import com.uade.crm.contracts.clientes.EstadoSituacionCrediticia;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Requirement: Degradación ante fallas de la fuente (decorator de resiliencia, design D5 del change 05). */
class SituacionCrediticiaResilienteTest {

    private static final Duration TIMEOUT = Duration.ofMillis(200);
    private static final Duration VENTANA = Duration.ofMillis(300);
    private static final ConsultaSituacion CONSULTA = new ConsultaSituacion(UUID.randomUUID(), Cuil.de("27-30111222-5"));

    private final FuenteDePrueba fuente = new FuenteDePrueba(TIMEOUT, Clock.systemUTC());
    private final CircuitBreaker circuito = CircuitBreaker.of("situacionCrediticia", CircuitBreakerConfig.custom()
            .slidingWindowSize(4)
            .minimumNumberOfCalls(4)
            .failureRateThreshold(50)
            .waitDurationInOpenState(VENTANA)
            .permittedNumberOfCallsInHalfOpenState(1)
            .build());
    private final SituacionCrediticiaResiliente resiliente = new SituacionCrediticiaResiliente(fuente, circuito);

    @Test
    void conLaFuenteNormalDevuelveSuResultado() {
        assertThat(resiliente.consultar(CONSULTA).estado()).isEqualTo(EstadoSituacionCrediticia.CON_DATOS);
    }

    @Test
    void fuenteCaidaDevuelveNoDisponible() {
        fuente.caer();

        var situacion = resiliente.consultar(CONSULTA);

        assertThat(situacion.estado()).isEqualTo(EstadoSituacionCrediticia.NO_DISPONIBLE);
        assertThat(situacion.situacion()).isNull();
    }

    @Test
    void fuenteLentaDevuelveNoDisponibleCercaDelTimeout() {
        fuente.demorar(Duration.ofSeconds(5));
        long inicio = System.nanoTime();

        assertThat(resiliente.consultar(CONSULTA).estado()).isEqualTo(EstadoSituacionCrediticia.NO_DISPONIBLE);
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void unaExcepcionInesperadaTambienEsNoDisponibleYCuentaComoFalla() {
        fuente.fallarCon(new IllegalStateException("error de programación"));

        assertThat(resiliente.consultar(CONSULTA).estado()).isEqualTo(EstadoSituacionCrediticia.NO_DISPONIBLE);
        assertThat(circuito.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void conElCircuitoAbiertoNoLlamaALaFuente() {
        abrirCircuito();
        fuente.restablecer();

        assertThat(resiliente.consultar(CONSULTA).estado()).isEqualTo(EstadoSituacionCrediticia.NO_DISPONIBLE);
        assertThat(fuente.llamadas()).isZero();
    }

    @Test
    void despuesDeLaVentanaDeEsperaVuelveAConsultarLaFuente() throws Exception {
        abrirCircuito();
        fuente.restablecer();

        Thread.sleep(VENTANA.plusMillis(100).toMillis());

        assertThat(resiliente.consultar(CONSULTA).estado()).isEqualTo(EstadoSituacionCrediticia.CON_DATOS);
        assertThat(fuente.llamadas()).isEqualTo(1);
    }

    private void abrirCircuito() {
        fuente.caer();
        for (int i = 0; i < 4; i++) {
            resiliente.consultar(CONSULTA);
        }
        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
