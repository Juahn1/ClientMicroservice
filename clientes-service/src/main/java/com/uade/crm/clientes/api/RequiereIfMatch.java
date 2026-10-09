package com.uade.crm.clientes.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un endpoint que exige {@code If-Match} (design D4 del change 03). Lo valida {@link IfMatchInterceptor} antes
 * de leer y validar el cuerpo, así un request sin el header recibe 428 y no 422.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiereIfMatch {
}
