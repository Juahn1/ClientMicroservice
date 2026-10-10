package com.uade.crm.clientes.infrastructure.precalificacion;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Reemplaza la fuente de la situación crediticia por una {@link FuenteDePrueba} en los tests de integración (design
 * D10 del change 05). Lo hace al crear el bean {@value PrecalificacionConfig#FUENTE}, así que la cadena real (caché y
 * resiliencia) la envuelve igual que a la fuente de producción, y todas las clases de {@code @PruebaIntegracion} siguen
 * compartiendo un solo contexto. Los tests la obtienen por nombre y la restablecen antes de cada caso.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FuenteDePruebaConfig {

    @Bean
    static BeanPostProcessor reemplazarFuenteSituacionCrediticia(Environment entorno) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String nombre) {
                if (!PrecalificacionConfig.FUENTE.equals(nombre)) {
                    return bean;
                }
                Duration timeout = Binder.get(entorno)
                        .bind("clientes.situacion-crediticia.timeout", Duration.class).get();
                return new FuenteDePrueba(timeout, Clock.systemUTC());
            }
        };
    }
}
