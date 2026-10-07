package com.uade.crm.clientes.api;

import com.uade.crm.clientes.api.dto.AltaClienteRequest;
import com.uade.crm.clientes.api.dto.AltaClienteResponse;
import com.uade.crm.clientes.api.dto.ClienteResponse;
import com.uade.crm.clientes.api.dto.ClienteResumenResponse;
import com.uade.crm.clientes.api.dto.PaginaResponse;
import com.uade.crm.clientes.api.seguridad.RequiereClaveServicio;
import com.uade.crm.clientes.application.AltaClienteService;
import com.uade.crm.clientes.application.ConsultaClienteService;
import com.uade.crm.clientes.domain.ClienteFactory;
import com.uade.crm.clientes.domain.model.Cliente;
import com.uade.crm.clientes.domain.model.ResultadoAlta;
import com.uade.crm.contracts.Provincia;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/clientes")
@Tag(name = "Clientes", description = "Alta con deduplicación, ficha y búsqueda")
public class ClienteController {

    private final AltaClienteService alta;
    private final ConsultaClienteService consulta;
    private final ClienteFactory factory;

    public ClienteController(AltaClienteService alta, ConsultaClienteService consulta, ClienteFactory factory) {
        this.alta = alta;
        this.consulta = consulta;
        this.factory = factory;
    }

    @PostMapping
    @RequiereClaveServicio
    @SecurityRequirement(name = "claveServicio")
    @Operation(summary = "Alta de cliente con deduplicación por email",
            description = "Solo para servicios internos (leads). Si ya existe un cliente activo con el mismo email, "
                    + "responde 200 con ese cliente y no modifica su nombre, apellido ni provincia.")
    @ApiResponse(responseCode = "201", description = "Cliente nuevo")
    @ApiResponse(responseCode = "200", description = "El email ya pertenecía a un cliente activo")
    @ApiResponse(responseCode = "401", description = "Falta X-Api-Key o no es válida")
    @ApiResponse(responseCode = "422", description = "Datos inválidos (VALIDACION o TELEFONO_INVALIDO)")
    public ResponseEntity<AltaClienteResponse> alta(@Valid @RequestBody AltaClienteRequest request) {
        ResultadoAlta resultado = alta.registrar(request.aSolicitud(factory));
        return ResponseEntity.status(resultado.nuevo() ? HttpStatus.CREATED : HttpStatus.OK)
                .location(ubicacion(resultado.clienteId()))
                .body(AltaClienteResponse.de(resultado));
    }

    @GetMapping
    @Operation(summary = "Búsqueda paginada de clientes activos",
            description = "Los filtros se combinan con AND; sin filtros devuelve todos los activos. Orden: último "
                    + "contacto más reciente primero. `q` busca en nombre y apellido sin distinguir mayúsculas ni "
                    + "acentos. `size` por defecto 20, máximo 50 (un valor mayor se limita).")
    @ApiResponse(responseCode = "200", description = "Página de resultados (puede estar vacía)")
    @ApiResponse(responseCode = "422", description = "Parámetros inválidos (VALIDACION o TELEFONO_INVALIDO)")
    public PaginaResponse<ClienteResumenResponse> buscar(
            @Parameter(description = "Coincidencia exacta luego de normalizar", example = "11 1234-5678")
            @RequestParam(required = false) String telefono,
            @Parameter(description = "Coincidencia exacta sin distinguir mayúsculas")
            @RequestParam(required = false) String email,
            @Parameter(description = "Texto contenido en nombre y apellido", example = "lopez")
            @RequestParam(required = false) @Size(max = 241) String q,
            @RequestParam(required = false) Provincia provincia,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(required = false) @Min(1) Integer size) {
        return PaginaResponse.de(consulta.buscar(telefono, email, q, provincia, page, size),
                ClienteResumenResponse::de);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ficha del cliente",
            description = "Devuelve el header ETag con la versión del cliente (la usará el If-Match de la edición).")
    @ApiResponse(responseCode = "200", description = "Ficha del cliente activo")
    @ApiResponse(responseCode = "404", description = "CLIENTE_NO_ENCONTRADO: no existe o no está activo")
    public ResponseEntity<ClienteResponse> obtener(@PathVariable UUID id) {
        Cliente cliente = consulta.obtener(id);
        return ResponseEntity.ok()
                .eTag("W/\"" + cliente.version().orElse(0L) + "\"")
                .body(ClienteResponse.de(cliente));
    }

    /** Relativa, como en §5.3: el gateway la expone bajo /api. */
    private static URI ubicacion(UUID clienteId) {
        return URI.create("/clientes/" + clienteId);
    }
}
