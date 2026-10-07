package com.uade.crm.clientes.config;

import com.uade.crm.clientes.api.seguridad.ClaveServicioInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ClaveServicioInterceptor claveServicio;

    public WebConfig(ClaveServicioInterceptor claveServicio) {
        this.claveServicio = claveServicio;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(claveServicio);
    }
}
