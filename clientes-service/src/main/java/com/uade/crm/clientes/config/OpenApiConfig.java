package com.uade.crm.clientes.config;

import com.uade.crm.clientes.api.seguridad.ClaveServicioInterceptor;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI clientesOpenApi(@Value("${clientes.version:0.1.0}") String version) {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("claveServicio", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(ClaveServicioInterceptor.HEADER)
                        .description("Clave de servicio para los endpoints internos de escritura (la usa leads).")))
                .info(new Info()
                .title("Servicio de Clientes")
                .version(version)
                .description("Ficha única del comprador del CRM de calificación de leads: alta con deduplicación, "
                        + "consulta, CUIL y consentimientos, situación crediticia, notas y preferencias. "
                        + "A través del gateway se expone como /api/clientes/**."));
    }
}
