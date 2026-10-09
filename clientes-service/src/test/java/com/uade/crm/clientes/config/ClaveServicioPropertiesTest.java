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

/** La clave de servicio de leads es obligatoria y no puede ser corta (design D2). */
class ClaveServicioPropertiesTest {

    private static final String CLAVE = "solo-test-clave-de-servicio-leads-0001";

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(ConPropiedades.class)
            .withPropertyValues(
                    "clientes.telefono.region-por-defecto=AR",
                    "clientes.cuil.clave-cifrado=c29sby10ZXN0LWNsYXZlLWNpZnJhZG8tY3VpbC0zMmI=",
                    "clientes.cuil.clave-hash=c29sby10ZXN0LWNsYXZlLWhtYWMtZGVsLWN1aWwtMzJi",
                    "clientes.situacion-crediticia.modo=simulado",
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
    void arrancaConUnaClaveValida() {
        contexto.withPropertyValues("clientes.seguridad.clave-leads=" + CLAVE)
                .run(ctx -> assertThat(ctx).hasNotFailed()
                        .getBean(ClientesProperties.class)
                        .extracting(p -> p.seguridad().claveLeads()).isEqualTo(CLAVE));
    }

    @Test
    void noArrancaSinClave() {
        contexto.run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void noArrancaConUnPlaceholderSinResolver() {
        contexto.withPropertyValues("clientes.seguridad.clave-leads=${CLIENTES_CLAVE_LEADS_INEXISTENTE}")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void rechazaUnaClaveCorta() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClientesProperties.Seguridad("corta"))
                .withMessageContaining("al menos 32")
                .withMessageNotContaining("corta");
    }

    @Test
    void toStringNoExponeLaClave() {
        assertThat(new ClientesProperties.Seguridad(CLAVE).toString()).doesNotContain(CLAVE);
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
