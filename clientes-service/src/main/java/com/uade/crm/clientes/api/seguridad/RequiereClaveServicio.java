package com.uade.crm.clientes.api.seguridad;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un endpoint interno de escritura: solo lo puede usar un servicio que presente la clave configurada en
 * {@code X-Api-Key} (design D2). Lo valida {@link ClaveServicioInterceptor} antes de leer el cuerpo.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiereClaveServicio {
}
