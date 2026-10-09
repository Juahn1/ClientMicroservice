package com.uade.crm.pruebas;

import com.uade.crm.clientes.api.PrecondicionRequeridaException;
import com.uade.crm.clientes.api.seguridad.RequiereClaveServicio;
import com.uade.crm.clientes.application.ConfirmacionInvalidaException;
import com.uade.crm.clientes.application.EmailEnUsoException;
import com.uade.crm.clientes.application.VersionDesactualizadaException;
import com.uade.crm.clientes.domain.model.ClienteAnonimizadoException;
import com.uade.crm.clientes.domain.model.ClienteNoEncontradoException;
import com.uade.crm.contracts.Provincia;
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

    /** Endpoint interno de prueba para {@link RequiereClaveServicio}. */
    @RequiereClaveServicio
    @PostMapping("/pruebas/interno")
    AltaPrueba interno(@Valid @RequestBody AltaPrueba alta) {
        log.info("Alta interna recibida");
        return alta;
    }

    @PostMapping("/clientes/falla")
    void falla(@RequestBody AltaPrueba alta) {
        throw new IllegalStateException("Fallo procesando CUIL " + alta.cuil() + " y teléfono " + alta.telefono());
    }

    /** Lanza la excepción que corresponde a cada código del change 03, para probar su traducción. */
    @PostMapping("/pruebas/errores/{codigo}")
    void error(@PathVariable String codigo) {
        UUID id = UUID.randomUUID();
        throw switch (codigo) {
            case "CLIENTE_ANONIMIZADO" -> new ClienteAnonimizadoException(id);
            case "PRECONDICION_REQUERIDA" -> new PrecondicionRequeridaException();
            case "VERSION_DESACTUALIZADA" -> new VersionDesactualizadaException(id);
            case "EMAIL_EN_USO" -> new EmailEnUsoException(id);
            case "CONFIRMACION_INVALIDA" -> new ConfirmacionInvalidaException(id);
            default -> new IllegalArgumentException(codigo);
        };
    }

    public record AltaPrueba(@NotBlank @Size(min = 2, max = 120) String nombre, @Email String email, String cuil,
            String telefono, Provincia provincia) {
    }
}
