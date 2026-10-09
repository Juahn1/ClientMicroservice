package com.uade.crm.clientes.config;

import com.uade.crm.clientes.api.IfMatchInterceptor;
import com.uade.crm.clientes.api.seguridad.ClaveServicioInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ClaveServicioInterceptor claveServicio;
    private final IfMatchInterceptor ifMatch;

    public WebConfig(ClaveServicioInterceptor claveServicio, IfMatchInterceptor ifMatch) {
        this.claveServicio = claveServicio;
        this.ifMatch = ifMatch;
    }

    /** Los dos corren antes de leer el cuerpo: 401 y 428 tienen prioridad sobre un 422 de validación. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(claveServicio);
        registry.addInterceptor(ifMatch);
    }
}
