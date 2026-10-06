package com.uade.crm.clientes.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI clientesOpenApi(@Value("${clientes.version:0.1.0}") String version) {
        return new OpenAPI().info(new Info()
                .title("Servicio de Clientes")
                .version(version)
                .description("Ficha única del comprador del CRM de calificación de leads: alta con deduplicación, "
                        + "consulta, CUIL y consentimientos, situación crediticia, notas y preferencias. "
                        + "A través del gateway se expone como /api/clientes/**."));
    }
}
