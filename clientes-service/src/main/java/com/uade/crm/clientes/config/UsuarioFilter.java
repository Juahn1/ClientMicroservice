package com.uade.crm.clientes.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Toma el header opcional {@code X-Usuario} y lo deja en el MDC para que los logs registren quién operó (design D9).
 * Sin autenticación en la Primera Parte el valor es declarativo: sirve como rastro operativo, no como auditoría.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class UsuarioFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Usuario";
    public static final String MDC_KEY = "usuario";

    /** Evita inyección en logs: un valor fuera de este formato se ignora. */
    private static final Pattern FORMATO_VALIDO = Pattern.compile("[A-Za-z0-9._@-]{1,60}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String usuario = request.getHeader(HEADER);
        if (usuario == null || !FORMATO_VALIDO.matcher(usuario).matches()) {
            chain.doFilter(request, response);
            return;
        }
        MDC.put(MDC_KEY, usuario);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
