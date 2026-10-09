package com.uade.crm.clientes.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Rechaza con 428 los requests a un endpoint {@link RequiereIfMatch} que llegan sin {@code If-Match}. */
@Component
public class IfMatchInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String ifMatch = request.getHeader(HttpHeaders.IF_MATCH);
        if (handler instanceof HandlerMethod metodo && metodo.hasMethodAnnotation(RequiereIfMatch.class)
                && (ifMatch == null || ifMatch.isBlank())) {
            throw new PrecondicionRequeridaException();
        }
        return true;
    }
}
