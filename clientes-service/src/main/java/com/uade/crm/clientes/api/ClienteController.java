package com.uade.crm.clientes.api;

import com.uade.crm.clientes.api.dto.AltaClienteRequest;
import com.uade.crm.clientes.api.dto.AltaClienteResponse;
import com.uade.crm.clientes.api.dto.AnonimizacionRequest;
import com.uade.crm.clientes.api.dto.ClienteResponse;
import com.uade.crm.clientes.api.dto.ClienteResumenResponse;
import com.uade.crm.clientes.api.dto.EdicionClienteRequest;
import com.uade.crm.clientes.api.dto.PaginaResponse;
import com.uade.crm.clientes.api.seguridad.RequiereClaveServicio;
import com.uade.crm.clientes.application.AltaClienteService;
import com.uade.crm.clientes.application.AnonimizacionClienteService;
import com.uade.crm.clientes.application.ConsultaClienteService;
import com.uade.crm.clientes.application.EdicionClienteService;
import com.uade.crm.clientes.application.NoContactarService;
import com.uade.crm.clientes.application.VersionDesactualizadaException;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/clientes")
@Tag(name = "Clientes", description = "Alta con deduplicación, ficha, búsqueda, edición, no contactar y anonimización")
public class ClienteController {

    private final AltaClienteService alta;
    private final ConsultaClienteService consulta;
    private final EdicionClienteService edicion;
    private final NoContactarService noContactar;
    private final AnonimizacionClienteService anonimizacion;
    private final ClienteFactory factory;

    public ClienteController(AltaClienteService alta, ConsultaClienteService consulta, EdicionClienteService edicion,
            NoContactarService noContactar, AnonimizacionClienteService anonimizacion, ClienteFactory factory) {
        this.alta = alta;
        this.consulta = consulta;
        this.edicion = edicion;
        this.noContactar = noContactar;
        this.anonimizacion = anonimizacion;
        this.factory = factory;
    }

    @PostMapping
    @RequiereClaveServicio
    @SecurityRequirement(name = "claveServicio")
    @Operation(summary = "Alta de cliente con deduplicación por CUIL y por email",
            description = "Solo para servicios internos (leads). `cuil` y `consentimiento: true` van juntos, solo "
                    + "cuando el prospecto pidió financiación. Si el CUIL pertenece a un cliente activo responde 200 "
                    + "con `coincidencia` = CUIL, aunque el email sea otro; si no, si el email pertenece a uno, "
                    + "200 con `coincidencia` = EMAIL. Un recontacto no modifica nombre, apellido, provincia, email "
                    + "ni un CUIL ya registrado.")
    @ApiResponse(responseCode = "201", description = "Cliente nuevo")
    @ApiResponse(responseCode = "200", description = "El CUIL o el email ya pertenecían a un cliente activo")
    @ApiResponse(responseCode = "401", description = "Falta X-Api-Key o no es válida")
    @ApiResponse(responseCode = "422", description = "Datos inválidos (VALIDACION, TELEFONO_INVALIDO, CUIL_INVALIDO "
            + "o CONSENTIMIENTO_REQUERIDO)")
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
            description = "Devuelve el header ETag con la versión del cliente, que la edición exige en If-Match.")
    @ApiResponse(responseCode = "200", description = "Ficha del cliente activo")
    @ApiResponse(responseCode = "404", description = "CLIENTE_NO_ENCONTRADO: el ID nunca existió")
    @ApiResponse(responseCode = "410", description = "CLIENTE_ANONIMIZADO: el cliente fue anonimizado")
    public ResponseEntity<ClienteResponse> obtener(@PathVariable UUID id) {
        return ficha(consulta.obtener(id));
    }

    @PatchMapping("/{id}")
    @RequiereIfMatch
    @Operation(summary = "Edición parcial del cliente",
            description = "Para el personal del negocio; no requiere X-Api-Key. Exige If-Match con el ETag de la "
                    + "ficha (W/\"n\" o \"n\"). Los campos ausentes, nulos o en blanco no se modifican; los informados "
                    + "cumplen las reglas del alta. Si no hay cambios reales, el ETag no cambia. Header opcional "
                    + "X-Usuario para los logs.")
    @ApiResponse(responseCode = "200", description = "Ficha actualizada, con el ETag de la nueva versión")
    @ApiResponse(responseCode = "404", description = "CLIENTE_NO_ENCONTRADO")
    @ApiResponse(responseCode = "409", description = "EMAIL_EN_USO: el email pertenece a otro cliente activo")
    @ApiResponse(responseCode = "410", description = "CLIENTE_ANONIMIZADO")
    @ApiResponse(responseCode = "412", description = "VERSION_DESACTUALIZADA: la ficha cambió; volvé a consultarla")
    @ApiResponse(responseCode = "422", description = "Datos inválidos (VALIDACION o TELEFONO_INVALIDO)")
    @ApiResponse(responseCode = "428", description = "PRECONDICION_REQUERIDA: falta If-Match")
    public ResponseEntity<ClienteResponse> editar(@PathVariable UUID id,
            @Parameter(description = "ETag de la ficha", example = "W/\"3\"")
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody EdicionClienteRequest request) {
        long version = VersionEtag.version(ifMatch).orElseThrow(() -> new VersionDesactualizadaException(id));
        return ficha(edicion.editar(id, version, request.aCambios(factory)));
    }

    @PutMapping("/{id}/no-contactar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marcar al cliente como no contactar",
            description = "El cliente pidió que no lo llamen más. Conserva la ficha y sigue activo; un nuevo alta con "
                    + "su email (recontacto) quita la marca. Idempotente, sin If-Match ni X-Api-Key.")
    @ApiResponse(responseCode = "204", description = "Marcado (o ya lo estaba)")
    @ApiResponse(responseCode = "404", description = "CLIENTE_NO_ENCONTRADO")
    @ApiResponse(responseCode = "410", description = "CLIENTE_ANONIMIZADO")
    public void marcarNoContactar(@PathVariable UUID id) {
        noContactar.marcar(id);
    }

    @DeleteMapping("/{id}/no-contactar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Quitar la marca no contactar", description = "Idempotente, sin If-Match ni X-Api-Key.")
    @ApiResponse(responseCode = "204", description = "Desmarcado (o no lo estaba)")
    @ApiResponse(responseCode = "404", description = "CLIENTE_NO_ENCONTRADO")
    @ApiResponse(responseCode = "410", description = "CLIENTE_ANONIMIZADO")
    public void quitarNoContactar(@PathVariable UUID id) {
        noContactar.quitar(id);
    }

    @PostMapping("/{id}/anonimizacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Anonimizar al cliente (derecho de supresión, Ley 25.326)",
            description = "Elimina nombre, apellido, email, teléfonos y localidad; conserva el id, la provincia, los "
                    + "canales, el contador y las fechas. Exige el email del cliente como confirmación. Es "
                    + "irreversible e idempotente: repetirla sobre un anonimizado responde 204. Sin X-Api-Key.")
    @ApiResponse(responseCode = "204", description = "Anonimizado (o ya lo estaba)")
    @ApiResponse(responseCode = "404", description = "CLIENTE_NO_ENCONTRADO")
    @ApiResponse(responseCode = "422", description = "CONFIRMACION_INVALIDA (email distinto) o VALIDACION")
    public void anonimizar(@PathVariable UUID id, @Valid @RequestBody AnonimizacionRequest request) {
        anonimizacion.anonimizar(id, request.email());
    }

    private static ResponseEntity<ClienteResponse> ficha(Cliente cliente) {
        return ResponseEntity.ok()
                .eTag(VersionEtag.de(cliente.version().orElse(0L)))
                .body(ClienteResponse.de(cliente));
    }

    /** Relativa, como en §5.3: el gateway la expone bajo /api. */
    private static URI ubicacion(UUID clienteId) {
        return URI.create("/clientes/" + clienteId);
    }
}
