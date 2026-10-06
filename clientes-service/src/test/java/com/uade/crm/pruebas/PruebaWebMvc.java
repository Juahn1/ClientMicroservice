package com.uade.crm.pruebas;

import com.uade.crm.clientes.config.ClientesProperties;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Slice web con {@link PruebaController}. Registra {@link ClientesProperties} porque el slice
 * no aplica el {@code @ConfigurationPropertiesScan} de la aplicación.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebMvcTest(controllers = PruebaController.class)
@Import(PruebaController.class)
@EnableConfigurationProperties(ClientesProperties.class)
@ActiveProfiles("test")
public @interface PruebaWebMvc {
}
