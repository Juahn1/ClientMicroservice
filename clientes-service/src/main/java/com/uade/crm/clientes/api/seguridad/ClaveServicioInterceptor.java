package com.uade.crm.clientes.api.seguridad;

import com.uade.crm.clientes.config.ClientesProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Valida la clave de servicio en los handlers anotados con {@link RequiereClaveServicio} (design D2 del change 02 y
 * D7 del change 05). Compara solo contra la clave del consumidor que pide el handler: la clave de un servicio no abre
 * los endpoints de otro. Corre antes de que Spring lea y valide el cuerpo, así que un request sin clave recibe 401 y
 * no 422. La validación vive en el servicio y no solo en el gateway: un request que llega directo por la red interna
 * también la necesita.
 */
@Component
public class ClaveServicioInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Api-Key";

    private static final Logger log = LoggerFactory.getLogger(ClaveServicioInterceptor.class);

    private final Map<Consumidor, byte[]> claves = new EnumMap<>(Consumidor.class);

    public ClaveServicioInterceptor(ClientesProperties properties) {
        claves.put(Consumidor.LEADS, bytes(properties.seguridad().claveLeads()));
        claves.put(Consumidor.CALIFICACION, bytes(properties.seguridad().claveCalificacion()));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod metodo)) {
            return true;
        }
        RequiereClaveServicio anotacion = anotacion(metodo);
        if (anotacion == null) {
            return true;
        }
        if (!claveValida(anotacion.value(), request.getHeader(HEADER))) {
            throw new ClaveServicioInvalidaException();
        }
        log.debug("Acceso de servicio autorizado: consumidor={}", anotacion.value());
        return true;
    }

    private static RequiereClaveServicio anotacion(HandlerMethod metodo) {
        RequiereClaveServicio enMetodo = metodo.getMethodAnnotation(RequiereClaveServicio.class);
        return enMetodo != null ? enMetodo : metodo.getBeanType().getAnnotation(RequiereClaveServicio.class);
    }

    /** Comparación en tiempo constante: el tiempo de respuesta no revela cuántos caracteres coinciden. */
    private boolean claveValida(Consumidor consumidor, String recibida) {
        return recibida != null && MessageDigest.isEqual(claves.get(consumidor), bytes(recibida));
    }

    private static byte[] bytes(String clave) {
        return clave.getBytes(StandardCharsets.UTF_8);
    }
}
