package com.uade.crm.clientes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Aplicación completa contra PostgreSQL real. Todas las clases que la usan comparten el mismo contexto de Spring
 * (y por lo tanto el mismo contenedor), así que cada una tiene que limpiar los datos que crea.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresDePrueba.class)
public @interface PruebaIntegracion {
}
