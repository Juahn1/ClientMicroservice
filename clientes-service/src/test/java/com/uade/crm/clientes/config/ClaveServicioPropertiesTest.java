package com.uade.crm.clientes.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.context.annotation.Configuration;

/**
 * Las claves de servicio de leads y de calificacion son obligatorias, no pueden ser cortas y tienen que ser distintas
 * (design D2 del change 02 y D7 del change 05).
 */
class ClaveServicioPropertiesTest {

    private static final String CLAVE = "solo-test-clave-de-servicio-leads-0001";
    private static final String CLAVE_CALIFICACION = "solo-test-clave-de-servicio-calificacion-0001";

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(ConPropiedades.class)
            .withPropertyValues(
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
                    "clientes.historial.zona-fecha=America/Argentina/Buenos_Aires",
                    "clientes.historial.reintentos.intentos=3",
                    "clientes.historial.reintentos.espera-inicial=50ms",
                    "clientes.historial.reintentos.multiplicador=2",
                    "clientes.kafka.particiones=3",
                    "clientes.kafka.replicas=1",
                    "clientes.errores.tipo-base=https://crm.uade.local/errores/");

    @Test
    void arrancaConClavesValidas() {
        contexto.withPropertyValues("clientes.seguridad.clave-leads=" + CLAVE,
                        "clientes.seguridad.clave-calificacion=" + CLAVE_CALIFICACION)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    var seguridad = ctx.getBean(ClientesProperties.class).seguridad();
                    assertThat(seguridad.claveLeads()).isEqualTo(CLAVE);
                    assertThat(seguridad.claveCalificacion()).isEqualTo(CLAVE_CALIFICACION);
                });
    }

    @Test
    void noArrancaSinClaveDeLeads() {
        contexto.withPropertyValues("clientes.seguridad.clave-calificacion=" + CLAVE_CALIFICACION)
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void noArrancaSinClaveDeCalificacion() {
        contexto.withPropertyValues("clientes.seguridad.clave-leads=" + CLAVE)
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void noArrancaConUnPlaceholderSinResolver() {
        contexto.withPropertyValues("clientes.seguridad.clave-leads=${CLIENTES_CLAVE_LEADS_INEXISTENTE}",
                        "clientes.seguridad.clave-calificacion=" + CLAVE_CALIFICACION)
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void rechazaUnPlaceholderSinResolverDeCalificacion() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClientesProperties.Seguridad(CLAVE, "${CLIENTES_CLAVE_CALIFICACION}"))
                .withMessageContaining("clientes.seguridad.clave-calificacion")
                .withMessageContaining("CLIENTES_CLAVE_CALIFICACION");
    }

    @Test
    void rechazaUnaClaveCorta() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClientesProperties.Seguridad("corta", CLAVE_CALIFICACION))
                .withMessageContaining("al menos 32")
                .withMessageNotContaining("corta");
    }

    @Test
    void rechazaUnaClaveDeCalificacionCorta() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClientesProperties.Seguridad(CLAVE, "corta"))
                .withMessageContaining("clientes.seguridad.clave-calificacion")
                .withMessageContaining("al menos 32")
                .withMessageNotContaining("corta");
    }

    @Test
    void rechazaClavesIguales() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClientesProperties.Seguridad(CLAVE, CLAVE))
                .withMessageContaining("distintas")
                .withMessageNotContaining(CLAVE);
    }

    @Test
    void toStringNoExponeLasClaves() {
        assertThat(new ClientesProperties.Seguridad(CLAVE, CLAVE_CALIFICACION).toString())
                .doesNotContain(CLAVE).doesNotContain(CLAVE_CALIFICACION);
    }

    @Test
    void elTamanioPorDefectoNoPuedeSuperarAlMaximo() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClientesProperties.Paginacion(50, 60));
    }

    @Configuration
    @EnableConfigurationProperties(ClientesProperties.class)
    static class ConPropiedades {
    }
}
