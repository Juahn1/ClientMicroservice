package com.uade.crm.clientes.api.seguridad;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un endpoint reservado a un servicio: solo lo puede usar quien presente en {@code X-Api-Key} la clave
 * configurada para ese {@link Consumidor} (design D2 del change 02 y D7 del change 05). Lo valida
 * {@link ClaveServicioInterceptor} antes de leer el cuerpo. No tiene valor por defecto: un endpoint nuevo no puede
 * quedar abierto a la clave de otro servicio por omisión.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiereClaveServicio {

    Consumidor value();
}
