package com.uade.crm.clientes.config;

import com.uade.crm.clientes.api.IfMatchInterceptor;
import com.uade.crm.clientes.api.seguridad.ClaveServicioInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ClaveServicioInterceptor claveServicio;
    private final IfMatchInterceptor ifMatch;

    /** Orígenes permitidos para CORS, leídos de `cors.allowed-origins` (coma separados). */
    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

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

    /**
     * CORS para el frontend: solo los orígenes declarados en `cors.allowed-origins`.
     * Expone ETag (necesario para If-Match) y admite los headers de servicio.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("ETag", "Location")
                .allowCredentials(true);
    }
}
