package com.uade.crm.clientes.infrastructure.precalificacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.uade.crm.clientes.domain.model.SituacionCrediticia;
import com.uade.crm.clientes.domain.port.CacheSituacionCrediticia;
import com.uade.crm.clientes.domain.port.ConsultaSituacion;
import com.uade.crm.clientes.domain.valor.Cuil;
import com.uade.crm.contracts.clientes.EstadoSituacionCrediticia;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Requirement: Caché con vigencia (decorator, design D4 del change 05). */
class SituacionCrediticiaConCacheTest {

    private static final Duration VIGENCIA = Duration.ofDays(30);
    private static final ConsultaSituacion CONSULTA = new ConsultaSituacion(UUID.randomUUID(), Cuil.de("27-30111222-5"));

    private final RelojControlable reloj = new RelojControlable(Instant.parse("2026-10-10T12:00:00.123456789Z"));
    private final FuenteDePrueba fuente = new FuenteDePrueba(Duration.ofMillis(200), reloj);
    private final CacheEnMemoria cache = new CacheEnMemoria();
    private final SituacionCrediticiaConCache conCache = new SituacionCrediticiaConCache(fuente, cache, VIGENCIA, reloj);

    @Test
    void dentroDeLaVigenciaConsultaLaFuenteUnaSolaVez() {
        SituacionCrediticia primera = conCache.consultar(CONSULTA);
        reloj.avanzar(Duration.ofDays(29));
        SituacionCrediticia segunda = conCache.consultar(CONSULTA);

        assertThat(fuente.llamadas()).isEqualTo(1);
        assertThat(segunda).isEqualTo(primera);
    }

    @Test
    void laRespuestaQueLlenaLaCacheTieneLaPrecisionDeLaBase() {
        assertThat(conCache.consultar(CONSULTA).consultadaEn()).isEqualTo(Instant.parse("2026-10-10T12:00:00.123456Z"));
    }

    @Test
    void vencidoVuelveAConsultarYActualiza() {
        conCache.consultar(CONSULTA);
        reloj.avanzar(VIGENCIA.plusSeconds(1));

        SituacionCrediticia nueva = conCache.consultar(CONSULTA);

        assertThat(fuente.llamadas()).isEqualTo(2);
        assertThat(nueva.consultadaEn()).isAfter(Instant.parse("2026-11-09T00:00:00Z"));
        assertThat(cache.guardadas.get(CONSULTA.clienteId())).isEqualTo(nueva);
    }

    @Test
    void unNoDisponibleNoSeGuarda() {
        fuente.caer();
        // La resiliencia real traduce la caída a NO_DISPONIBLE; con 1 sola falla el circuito no se abre.
        var resiliente = new SituacionCrediticiaResiliente(fuente, CircuitBreaker.ofDefaults("prueba"));
        var sobreResiliente = new SituacionCrediticiaConCache(resiliente, cache, VIGENCIA, reloj);

        assertThat(sobreResiliente.consultar(CONSULTA).estado()).isEqualTo(EstadoSituacionCrediticia.NO_DISPONIBLE);
        assertThat(cache.guardadas).isEmpty();

        fuente.restablecer();
        assertThat(sobreResiliente.consultar(CONSULTA).estado()).isEqualTo(EstadoSituacionCrediticia.CON_DATOS);
        assertThat(fuente.llamadas()).isEqualTo(1);
    }

    @Test
    void siFallaElGuardadoRespondeIgual() {
        cache.fallarAlGuardar = true;

        assertThat(conCache.consultar(CONSULTA).estado()).isEqualTo(EstadoSituacionCrediticia.CON_DATOS);
    }

    private static final class CacheEnMemoria implements CacheSituacionCrediticia {

        final Map<UUID, SituacionCrediticia> guardadas = new HashMap<>();
        final Map<UUID, Instant> vigentesHasta = new HashMap<>();
        boolean fallarAlGuardar;

        @Override
        public Optional<SituacionCrediticia> vigente(UUID clienteId, Instant ahora) {
            Instant hasta = vigentesHasta.get(clienteId);
            return hasta != null && hasta.isAfter(ahora) ? Optional.of(guardadas.get(clienteId)) : Optional.empty();
        }

        @Override
        public void guardar(UUID clienteId, SituacionCrediticia situacion, Instant vigenteHasta) {
            if (fallarAlGuardar) {
                throw new IllegalStateException("base caída");
            }
            guardadas.put(clienteId, situacion);
            vigentesHasta.put(clienteId, vigenteHasta);
        }

        @Override
        public void eliminar(UUID clienteId) {
            guardadas.remove(clienteId);
            vigentesHasta.remove(clienteId);
        }
    }

    private static final class RelojControlable extends Clock {

        private Instant ahora;

        RelojControlable(Instant ahora) {
            this.ahora = ahora;
        }

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
