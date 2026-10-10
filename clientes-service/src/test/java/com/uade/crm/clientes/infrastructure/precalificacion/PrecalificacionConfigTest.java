package com.uade.crm.clientes.infrastructure.precalificacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.uade.crm.clientes.config.ClientesProperties;
import com.uade.crm.clientes.domain.model.FuenteSituacion;
import com.uade.crm.clientes.domain.model.SituacionCrediticia;
import com.uade.crm.clientes.domain.port.CacheSituacionCrediticia;
import com.uade.crm.clientes.domain.port.ConsultaSituacion;
import com.uade.crm.clientes.domain.port.SituacionCrediticiaPort;
import com.uade.crm.clientes.domain.valor.Cuil;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Requirement: Selección de la fuente por configuración (design D6 del change 05). */
class PrecalificacionConfigTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(Dependencias.class, PrecalificacionConfig.class)
            .withPropertyValues(
                    "clientes.seguridad.clave-leads=solo-test-clave-de-servicio-leads-0001",
                    "clientes.seguridad.clave-calificacion=solo-test-clave-de-servicio-calificacion-0001",
                    "clientes.telefono.region-por-defecto=AR",
                    "clientes.cuil.clave-cifrado=c29sby10ZXN0LWNsYXZlLWNpZnJhZG8tY3VpbC0zMmI=",
                    "clientes.cuil.clave-hash=c29sby10ZXN0LWNsYXZlLWhtYWMtZGVsLWN1aWwtMzJi",
                    "clientes.consentimiento.version-texto=v1",
                    "clientes.situacion-crediticia.vigencia=30d",
                    "clientes.situacion-crediticia.timeout=2s",
                    "clientes.situacion-crediticia.simulado.demora=0ms",
                    "clientes.interesados.tolerancia-precio=0.10",
                    "clientes.paginacion.tamanio-maximo=50",
                    "clientes.paginacion.tamanio-por-defecto=20",
                    "clientes.kafka.topicos.cliente-actualizado=a",
                    "clientes.kafka.topicos.cliente-anonimizado=b",
                    "clientes.kafka.particiones=3",
                    "clientes.kafka.replicas=1",
                    "clientes.errores.tipo-base=https://crm.uade.local/errores/");

    @Test
    void enModoSimuladoLaCadenaUsaElAdaptadorSimulado() {
        contexto.withPropertyValues("clientes.situacion-crediticia.modo=simulado").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(PrecalificacionConfig.FUENTE)).isInstanceOf(SimuladaSituacionAdapter.class);
            SituacionCrediticia situacion = ctx.getBean(SituacionCrediticiaPort.class)
                    .consultar(new ConsultaSituacion(UUID.randomUUID(), Cuil.de("27-30111222-5")));
            assertThat(situacion.fuente()).isEqualTo(FuenteSituacion.SIMULADA);
        });
    }

    @Test
    void enModoBcraNoArrancaYNombraLaPropiedad() {
        contexto.withPropertyValues("clientes.situacion-crediticia.modo=bcra").run(ctx -> assertThat(ctx)
                .hasFailed()
                .getFailure()
                .rootCause()
                .hasMessageContaining("clientes.situacion-crediticia.modo"));
    }

    @Configuration
    @EnableConfigurationProperties(ClientesProperties.class)
    static class Dependencias {

        @Bean
        Clock reloj() {
            return Clock.systemUTC();
        }

        @Bean
        CircuitBreakerRegistry circuitos() {
            return CircuitBreakerRegistry.ofDefaults();
        }

        @Bean
        CacheSituacionCrediticia cache() {
            return new CacheSituacionCrediticia() {
                @Override
                public Optional<SituacionCrediticia> vigente(UUID clienteId, Instant ahora) {
                    return Optional.empty();
                }

                @Override
                public void guardar(UUID clienteId, SituacionCrediticia situacion, Instant vigenteHasta) {
                }

                @Override
                public void eliminar(UUID clienteId) {
                }
            };
        }
    }
}
