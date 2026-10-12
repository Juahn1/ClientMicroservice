package com.uade.crm.clientes.infrastructure.precalificacion;

import com.uade.crm.clientes.domain.model.SituacionCrediticia;
import com.uade.crm.clientes.domain.port.CacheSituacionCrediticia;
import com.uade.crm.clientes.domain.port.ConsultaSituacion;
import com.uade.crm.clientes.domain.port.SituacionCrediticiaPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorator de caché por cliente (S-01, design D2 y D4 del change 05). Va afuera de la resiliencia: un acierto no
 * llega al circuit breaker y un {@code NO_DISPONIBLE} llega acá como resultado, que no se guarda. La vigencia se cuenta
 * desde {@code consultadaEn}.
 */
public class SituacionCrediticiaConCache implements SituacionCrediticiaPort {

    private static final Logger log = LoggerFactory.getLogger(SituacionCrediticiaConCache.class);

    private final SituacionCrediticiaPort siguiente;
    private final CacheSituacionCrediticia cache;
    private final Duration vigencia;
    private final Clock reloj;

    public SituacionCrediticiaConCache(SituacionCrediticiaPort siguiente, CacheSituacionCrediticia cache,
            Duration vigencia, Clock reloj) {
        this.siguiente = Objects.requireNonNull(siguiente, "siguiente");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.vigencia = Objects.requireNonNull(vigencia, "vigencia");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    @Override
    public SituacionCrediticia consultar(ConsultaSituacion consulta) {
        Optional<SituacionCrediticia> guardada = cache.vigente(consulta.clienteId(), reloj.instant());
        if (guardada.isPresent()) {
            log.debug("Situación crediticia del cliente {} desde la caché", consulta.clienteId());
            return guardada.get();
        }
        log.debug("Situación crediticia del cliente {} sin caché vigente: se consulta la fuente", consulta.clienteId());
        SituacionCrediticia resultado = siguiente.consultar(consulta);
        if (!resultado.esCacheable()) {
            return resultado;
        }
        SituacionCrediticia normalizado = comoSeGuarda(resultado);
        try {
            cache.guardar(consulta.clienteId(), normalizado, normalizado.consultadaEn().plus(vigencia));
        } catch (RuntimeException falla) {
            // La consulta a la fuente ya se hizo y su resultado es válido: se responde igual y se reintenta la próxima.
            log.warn("No se pudo guardar en caché la situación crediticia del cliente {}", consulta.clienteId(), falla);
        }
        return normalizado;
    }

    /**
     * La base guarda microsegundos: se trunca antes de responder para que la respuesta de la consulta que llenó la
     * caché sea igual a las que se sirven desde ella.
     */
    private static SituacionCrediticia comoSeGuarda(SituacionCrediticia s) {
        Instant consultadaEn = s.consultadaEn().truncatedTo(ChronoUnit.MICROS);
        return new SituacionCrediticia(s.estado(), s.situacion(), s.periodo(), s.fuente(), consultadaEn);
    }
}
