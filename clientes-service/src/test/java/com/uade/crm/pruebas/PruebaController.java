package com.uade.crm.pruebas;

import com.uade.crm.clientes.domain.model.ClienteNoEncontradoException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller solo de test (design D9): ejercita el manejo transversal de errores y logs
 * hasta que lleguen los endpoints reales en los changes 02 y 04.
 * Vive fuera de {@code com.uade.crm.clientes} para que no lo levante el component scan de la aplicación.
 */
@RestController
public class PruebaController {

    private static final Logger log = LoggerFactory.getLogger(PruebaController.class);

    @GetMapping("/clientes/{id}")
    void consultar(@PathVariable UUID id) {
        log.info("Consulta de cliente {}", id);
        throw new ClienteNoEncontradoException(id);
    }

    /** Loguea los datos crudos a propósito: el enmascarado tiene que ocurrir en logback. */
    @PostMapping("/clientes")
    AltaPrueba alta(@Valid @RequestBody AltaPrueba alta) {
        log.info("Alta recibida: nombre={}, cuil={}, telefono={}", alta.nombre(), alta.cuil(), alta.telefono());
        return alta;
    }

    @PostMapping("/clientes/falla")
    void falla(@RequestBody AltaPrueba alta) {
        throw new IllegalStateException("Fallo procesando CUIL " + alta.cuil() + " y teléfono " + alta.telefono());
    }

    public record AltaPrueba(@NotBlank @Size(min = 2, max = 120) String nombre, @Email String email, String cuil,
            String telefono) {
    }
}
