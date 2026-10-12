package com.uade.crm.clientes.infrastructure.precalificacion;

import com.uade.crm.clientes.domain.model.SituacionCrediticia;
import com.uade.crm.clientes.domain.port.ConsultaSituacion;
import com.uade.crm.clientes.domain.port.FuenteNoDisponibleException;
import com.uade.crm.clientes.domain.port.SituacionCrediticiaPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorator con circuit breaker sobre la fuente (design D5 del change 05). Toda falla de la fuente, incluido el
 * timeout que la fuente misma impone, cuenta para el circuito y termina en {@code NO_DISPONIBLE}: la consulta nunca
 * responde 5xx por culpa de la fuente. Con el circuito abierto no llama a la fuente.
 */
public class SituacionCrediticiaResiliente implements SituacionCrediticiaPort {

    private static final Logger log = LoggerFactory.getLogger(SituacionCrediticiaResiliente.class);

    private final SituacionCrediticiaPort fuente;
    private final CircuitBreaker circuito;

    public SituacionCrediticiaResiliente(SituacionCrediticiaPort fuente, CircuitBreaker circuito) {
        this.fuente = Objects.requireNonNull(fuente, "fuente");
        this.circuito = Objects.requireNonNull(circuito, "circuito");
    }

    @Override
    public SituacionCrediticia consultar(ConsultaSituacion consulta) {
        try {
            return circuito.executeSupplier(() -> fuente.consultar(consulta));
        } catch (CallNotPermittedException abierto) {
            log.warn("Situación crediticia del cliente {} no disponible: circuito {}", consulta.clienteId(),
                    circuito.getState());
        } catch (FuenteNoDisponibleException falla) {
            log.warn("Situación crediticia del cliente {} no disponible: {} (circuito {})", consulta.clienteId(),
                    falla.getMessage(), circuito.getState());
        } catch (RuntimeException inesperada) {
            log.warn("Situación crediticia del cliente {} no disponible por una falla inesperada de la fuente "
                    + "(circuito {})", consulta.clienteId(), circuito.getState(), inesperada);
        }
        return SituacionCrediticia.noDisponible();
    }
}
