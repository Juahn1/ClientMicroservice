package com.uade.crm.clientes.infrastructure.precalificacion;

import com.uade.crm.clientes.config.ClientesProperties;
import com.uade.crm.clientes.domain.port.CacheSituacionCrediticia;
import com.uade.crm.clientes.domain.port.SituacionCrediticiaPort;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Arma la cadena de la situación crediticia (design D2 y D6 del change 05): caché → resiliencia → fuente. La fuente
 * se elige con {@code clientes.situacion-crediticia.modo} y es un bean aparte, {@value #FUENTE}, que los tests de
 * integración reemplazan; el resto de la aplicación solo ve la cadena.
 */
@Configuration
class PrecalificacionConfig {

    static final String FUENTE = "fuenteSituacionCrediticia";
    static final String CIRCUITO = "situacionCrediticia";

    @Bean(FUENTE)
    SituacionCrediticiaPort fuenteSituacionCrediticia(ClientesProperties properties, Clock reloj) {
        ClientesProperties.SituacionCrediticia config = properties.situacionCrediticia();
        return switch (config.modo()) {
            case SIMULADO -> new SimuladaSituacionAdapter(config.timeout(), config.simulado().demora(),
                    config.simulado().forzarNoDisponible(), reloj);
            case BCRA -> throw new IllegalStateException("clientes.situacion-crediticia.modo = bcra todavía no tiene "
                    + "adaptador (llega con el change clientes-09); usar modo = simulado");
        };
    }

    @Bean
    @Primary
    SituacionCrediticiaPort situacionCrediticia(@Qualifier(FUENTE) SituacionCrediticiaPort fuente,
            CacheSituacionCrediticia cache, CircuitBreakerRegistry circuitos, ClientesProperties properties,
            Clock reloj) {
        var resiliente = new SituacionCrediticiaResiliente(fuente, circuitos.circuitBreaker(CIRCUITO));
        return new SituacionCrediticiaConCache(resiliente, cache, properties.situacionCrediticia().vigencia(), reloj);
    }
}
