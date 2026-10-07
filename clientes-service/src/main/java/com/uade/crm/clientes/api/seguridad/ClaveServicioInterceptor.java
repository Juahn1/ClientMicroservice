package com.uade.crm.clientes.api.seguridad;

import com.uade.crm.clientes.config.ClientesProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Valida la clave de servicio en los handlers anotados con {@link RequiereClaveServicio} (design D2).
 * Corre antes de que Spring lea y valide el cuerpo, así que un request sin clave recibe 401 y no 422.
 * La validación vive en el servicio y no solo en el gateway: un request que llega directo por la red interna
 * también la necesita.
 */
@Component
public class ClaveServicioInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Api-Key";

    private final byte[] claveLeads;

    public ClaveServicioInterceptor(ClientesProperties properties) {
        this.claveLeads = properties.seguridad().claveLeads().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod metodo && requiereClave(metodo) && !claveValida(request.getHeader(HEADER))) {
            throw new ClaveServicioInvalidaException();
        }
        return true;
    }

    private static boolean requiereClave(HandlerMethod metodo) {
        return metodo.hasMethodAnnotation(RequiereClaveServicio.class)
                || metodo.getBeanType().isAnnotationPresent(RequiereClaveServicio.class);
    }

    /** Comparación en tiempo constante: el tiempo de respuesta no revela cuántos caracteres coinciden. */
    private boolean claveValida(String recibida) {
        return recibida != null && MessageDigest.isEqual(claveLeads, recibida.getBytes(StandardCharsets.UTF_8));
    }
}
