package com.uade.crm.clientes.api.error;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.uade.crm.clientes.api.PrecondicionRequeridaException;
import com.uade.crm.clientes.api.seguridad.ClaveServicioInterceptor;
import com.uade.crm.clientes.api.seguridad.ClaveServicioInvalidaException;
import com.uade.crm.clientes.application.ConfirmacionInvalidaException;
import com.uade.crm.clientes.application.EmailEnUsoException;
import com.uade.crm.clientes.application.VersionDesactualizadaException;
import com.uade.crm.clientes.config.ClientesProperties;
import com.uade.crm.clientes.domain.valor.CuilInvalidoException;
import com.uade.crm.clientes.domain.valor.TelefonoInvalidoException;
import com.uade.crm.clientes.config.CorrelationIdFilter;
import com.uade.crm.clientes.domain.model.ClienteAnonimizadoException;
import com.uade.crm.clientes.domain.model.ClienteNoEncontradoException;
import com.uade.crm.clientes.domain.model.ConsentimientoRequeridoException;
import com.uade.crm.clientes.domain.model.DatoInvalidoException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce todo error de la API a ProblemDetail (RFC 7807) con {@code codigo} y {@code correlationId}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final String tipoBase;

    public ApiExceptionHandler(ClientesProperties properties) {
        this.tipoBase = properties.errores().tipoBase();
    }

    @ExceptionHandler(ClienteNoEncontradoException.class)
    ResponseEntity<Object> clienteNoEncontrado(ClienteNoEncontradoException ex, NativeWebRequest request) {
        return problema(CodigoError.CLIENTE_NO_ENCONTRADO, ex.getMessage(), request);
    }

    /** 410 sin datos del cliente: solo se informa que existió y fue anonimizado (design D7). */
    @ExceptionHandler(ClienteAnonimizadoException.class)
    ResponseEntity<Object> clienteAnonimizado(ClienteAnonimizadoException ex, NativeWebRequest request) {
        return problema(CodigoError.CLIENTE_ANONIMIZADO, ex.getMessage(), request);
    }

    @ExceptionHandler(PrecondicionRequeridaException.class)
    ResponseEntity<Object> precondicionRequerida(PrecondicionRequeridaException ex, NativeWebRequest request) {
        return problema(CodigoError.PRECONDICION_REQUERIDA, ex.getMessage(), request);
    }

    @ExceptionHandler(VersionDesactualizadaException.class)
    ResponseEntity<Object> versionDesactualizada(VersionDesactualizadaException ex, NativeWebRequest request) {
        return problema(CodigoError.VERSION_DESACTUALIZADA, ex.getMessage(), request);
    }

    @ExceptionHandler(EmailEnUsoException.class)
    ResponseEntity<Object> emailEnUso(EmailEnUsoException ex, NativeWebRequest request) {
        return conErrorDeCampo(problema(CodigoError.EMAIL_EN_USO, ex.getMessage(), request), "email",
                "ya pertenece a otro cliente");
    }

    @ExceptionHandler(ConfirmacionInvalidaException.class)
    ResponseEntity<Object> confirmacionInvalida(ConfirmacionInvalidaException ex, NativeWebRequest request) {
        return conErrorDeCampo(problema(CodigoError.CONFIRMACION_INVALIDA, ex.getMessage(), request), "email",
                "no coincide con el email del cliente");
    }

    @ExceptionHandler(ClaveServicioInvalidaException.class)
    ResponseEntity<Object> claveServicioInvalida(ClaveServicioInvalidaException ex, NativeWebRequest request) {
        ResponseEntity<Object> respuesta = problema(CodigoError.CLAVE_SERVICIO_INVALIDA, ex.getMessage(), request);
        return ResponseEntity.status(respuesta.getStatusCode())
                .header(HttpHeaders.WWW_AUTHENTICATE,
                        "ApiKey realm=\"clientes\", header=\"" + ClaveServicioInterceptor.HEADER + "\"")
                .body(respuesta.getBody());
    }

    @ExceptionHandler(TelefonoInvalidoException.class)
    ResponseEntity<Object> telefonoInvalido(TelefonoInvalidoException ex, NativeWebRequest request) {
        return conErrorDeCampo(problema(CodigoError.TELEFONO_INVALIDO, ex.getMessage(), request), "telefono",
                ex.getMessage());
    }

    @ExceptionHandler(CuilInvalidoException.class)
    ResponseEntity<Object> cuilInvalido(CuilInvalidoException ex, NativeWebRequest request) {
        return conErrorDeCampo(problema(CodigoError.CUIL_INVALIDO, ex.getMessage(), request), "cuil",
                ex.getMessage());
    }

    @ExceptionHandler(ConsentimientoRequeridoException.class)
    ResponseEntity<Object> consentimientoRequerido(ConsentimientoRequeridoException ex, NativeWebRequest request) {
        return conErrorDeCampo(problema(CodigoError.CONSENTIMIENTO_REQUERIDO, ex.getMessage(), request),
                "consentimiento", ex.getMessage());
    }

    /** Regla del dominio sobre un campo que Bean Validation no puede expresar (design D4 del change 04). */
    @ExceptionHandler(DatoInvalidoException.class)
    ResponseEntity<Object> datoInvalido(DatoInvalidoException ex, NativeWebRequest request) {
        return conErrorDeCampo(problema(CodigoError.VALIDACION, "Datos inválidos", request), ex.campo(),
                ex.getMessage());
    }

    /** Asocia el error a un campo del formulario, con el mismo formato que los errores de validación. */
    private static ResponseEntity<Object> conErrorDeCampo(ResponseEntity<Object> respuesta, String campo,
            String mensaje) {
        if (respuesta.getBody() instanceof ProblemDetail problema) {
            problema.setProperty("errores", List.of(new ErrorDeCampo(campo, mensaje)));
        }
        return respuesta;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> errorInesperado(Exception ex, NativeWebRequest request) {
        log.error("Error no controlado", ex);
        return problema(CodigoError.ERROR_INTERNO, null, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorDeCampo> errores = ex.getBindingResult().getAllErrors().stream()
                .map(ApiExceptionHandler::errorDeCampo)
                .toList();
        return validacion(errores, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorDeCampo> errores = ex.getParameterValidationResults().stream()
                .flatMap(resultado -> resultado.getResolvableErrors().stream()
                        .map(error -> new ErrorDeCampo(
                                resultado.getMethodParameter().getParameterName(), mensaje(error))))
                .toList();
        return validacion(errores, request);
    }

    /**
     * Un JSON bien formado con un valor que no se puede convertir al tipo del campo (por ejemplo, una provincia
     * fuera del enum) es un error de validación de ese campo, no un cuerpo mal formado (design D9).
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex.getCause() instanceof InvalidFormatException formato && !formato.getPath().isEmpty()) {
            return validacion(List.of(new ErrorDeCampo(ruta(formato), mensajeDeFormato(formato))), request);
        }
        return problema(CodigoError.JSON_INVALIDO, "El cuerpo del request no es un JSON válido", request);
    }

    /**
     * Un parámetro de consulta con un valor que no se puede convertir (por ejemplo {@code provincia=BS AS}) es un
     * error de validación de ese parámetro, igual que en el cuerpo. Los demás casos (path, headers) siguen el
     * tratamiento genérico.
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (ex instanceof MethodArgumentTypeMismatchException argumento
                && argumento.getParameter().hasParameterAnnotation(RequestParam.class)) {
            Class<?> tipo = argumento.getRequiredType();
            String mensaje = tipo != null && tipo.isEnum() ? valoresAceptados(tipo) : "formato inválido";
            return validacion(List.of(new ErrorDeCampo(argumento.getName(), mensaje)), request);
        }
        return super.handleTypeMismatch(ex, headers, status, request);
    }

    private static String valoresAceptados(Class<?> enumeracion) {
        return "valor no permitido; valores aceptados: " + Arrays.stream(enumeracion.getEnumConstants())
                .map(Object::toString).collect(Collectors.joining(", "));
    }

    private static String ruta(InvalidFormatException ex) {
        return ex.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining(".")).replace(".[", "[");
    }

    private static String mensajeDeFormato(InvalidFormatException ex) {
        Class<?> tipo = ex.getTargetType();
        return tipo != null && tipo.isEnum() ? valoresAceptados(tipo) : "formato inválido";
    }

    /** Errores que Spring MVC resuelve solo (405, 415, ruta inexistente...): se completan con codigo genérico. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> respuesta = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (respuesta != null && respuesta.getBody() instanceof ProblemDetail problema
                && (problema.getProperties() == null || !problema.getProperties().containsKey("codigo"))) {
            completar(problema, statusCode.is5xxServerError() ? CodigoError.ERROR_INTERNO : CodigoError.SOLICITUD_INVALIDA,
                    request);
        }
        return respuesta;
    }

    private ResponseEntity<Object> validacion(List<ErrorDeCampo> errores, WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(CodigoError.VALIDACION.status(),
                "Hay campos que no cumplen las validaciones");
        completar(problema, CodigoError.VALIDACION, request);
        problema.setProperty("errores", errores);
        return ResponseEntity.status(CodigoError.VALIDACION.status()).body(problema);
    }

    private ResponseEntity<Object> problema(CodigoError codigo, @Nullable String detalle, WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(codigo.status(), detalle);
        completar(problema, codigo, request);
        return ResponseEntity.status(codigo.status()).body(problema);
    }

    private void completar(ProblemDetail problema, CodigoError codigo, WebRequest request) {
        problema.setType(URI.create(tipoBase + codigo.slug()));
        problema.setTitle(codigo.titulo());
        problema.setProperty("codigo", codigo.name());
        problema.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        if (request instanceof NativeWebRequest nativo
                && nativo.getNativeRequest() instanceof HttpServletRequest servlet) {
            problema.setInstance(URI.create(servlet.getRequestURI()));
        }
    }

    private static ErrorDeCampo errorDeCampo(ObjectError error) {
        String campo = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
        return new ErrorDeCampo(campo, mensaje(error));
    }

    private static String mensaje(MessageSourceResolvable error) {
        return error.getDefaultMessage();
    }

    public record ErrorDeCampo(String campo, String mensaje) {
    }
}
