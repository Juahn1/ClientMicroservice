package com.uade.crm.clientes.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Configuración del historial de calificaciones (design D9 del change 08). */
class HistorialPropertiesTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(ConPropiedades.class)
            .withPropertyValues(
                    "clientes.seguridad.clave-leads=solo-test-clave-de-servicio-leads-0001",
                    "clientes.seguridad.clave-calificacion=solo-test-clave-de-servicio-calificacion-0001",
                    "clientes.telefono.region-por-defecto=AR",
                    "clientes.cuil.clave-cifrado=c29sby10ZXN0LWNsYXZlLWNpZnJhZG8tY3VpbC0zMmI=",
                    "clientes.cuil.clave-hash=c29sby10ZXN0LWNsYXZlLWhtYWMtZGVsLWN1aWwtMzJi",
                    "clientes.consentimiento.version-texto=v1",
                    "clientes.situacion-crediticia.modo=simulado",
                    "clientes.situacion-crediticia.vigencia=30d",
                    "clientes.situacion-crediticia.timeout=2s",
                    "clientes.situacion-crediticia.simulado.demora=0ms",
                    "clientes.interesados.tolerancia-precio=0.10",
                    "clientes.paginacion.tamanio-maximo=50",
                    "clientes.paginacion.tamanio-por-defecto=20",
                    "clientes.kafka.topicos.cliente-actualizado=a",
                    "clientes.kafka.topicos.cliente-anonimizado=b",
                    "clientes.kafka.topicos.lead-calificado=c",
                    "clientes.kafka.topicos.lead-calificado-dlt=d",
                    "clientes.kafka.particiones=3",
                    "clientes.kafka.replicas=1",
                    "clientes.historial.reintentos.intentos=5",
                    "clientes.historial.reintentos.espera-inicial=2s",
                    "clientes.historial.reintentos.multiplicador=2",
                    "clientes.errores.tipo-base=https://crm.uade.local/errores/");

    @Test
    void cargaLaZonaYLosReintentos() {
        contexto.withPropertyValues("clientes.historial.zona-fecha=America/Argentina/Buenos_Aires").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            ClientesProperties.Historial historial = ctx.getBean(ClientesProperties.class).historial();
            assertThat(historial.zonaFecha()).isEqualTo(ZoneId.of("America/Argentina/Buenos_Aires"));
            assertThat(historial.reintentos().intentos()).isEqualTo(5);
            assertThat(historial.reintentos().esperaInicial()).isEqualTo(Duration.ofSeconds(2));
            assertThat(historial.reintentos().multiplicador()).isEqualTo(2.0);
        });
    }

    @Test
    void noArrancaConUnaZonaInvalida() {
        contexto.withPropertyValues("clientes.historial.zona-fecha=America/Atlantida")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void noArrancaSinZona() {
        contexto.run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void noArrancaConUnMultiplicadorMenorQueUno() {
        contexto.withPropertyValues("clientes.historial.zona-fecha=UTC",
                        "clientes.historial.reintentos.multiplicador=0.5")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Configuration
    @EnableConfigurationProperties(ClientesProperties.class)
    static class ConPropiedades {
    }
}
