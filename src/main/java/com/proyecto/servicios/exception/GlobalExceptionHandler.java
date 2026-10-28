package com.proyecto.servicios.exception;

import com.proyecto.servicios.model.GenericResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ApiErrorResponseWriter errorResponseWriter;

    public GlobalExceptionHandler(ApiErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<GenericResponse<Void>> handleValidationExceptions(BindException ex) {
        var bindingResult = ex.getBindingResult();
        Map<String, String> errores = new HashMap<>();
        bindingResult.getFieldErrors().forEach(error ->
                errores.put(error.getField(), error.getDefaultMessage()));
        bindingResult.getGlobalErrors().forEach(error ->
                errores.put(error.getObjectName(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "Error de validación en la solicitud", errores);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GenericResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        return response(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud está vacío o tiene un formato inválido");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<GenericResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return response(HttpStatus.BAD_REQUEST,
                "El parámetro '" + ex.getName() + "' tiene un formato o valor inválido");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<GenericResponse<Void>> handleMissingRequestParameter(MissingServletRequestParameterException ex) {
        return response(HttpStatus.BAD_REQUEST, "Falta el parámetro obligatorio '" + ex.getParameterName() + "'");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<GenericResponse<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El tipo de contenido de la solicitud no está soportado");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<GenericResponse<Void>> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return response(HttpStatus.NOT_ACCEPTABLE, "El formato de respuesta solicitado no está soportado");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<GenericResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        return response(HttpStatus.NOT_FOUND, "El recurso solicitado no existe");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<GenericResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "El método HTTP no está permitido para este recurso");
    }

    @ExceptionHandler({
            CurpDuplicadaException.class,
            RfcDuplicadoException.class,
            CorreoDuplicadoException.class,
            ClienteYaRegistradoException.class
    })
    public ResponseEntity<GenericResponse<Void>> handleConflictExceptions(RuntimeException ex) {
        return response(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<GenericResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return response(HttpStatus.CONFLICT,
                "La solicitud entra en conflicto con una restricción de integridad de datos");
    }

    @ExceptionHandler({
            ClienteNoEncontradoException.class,
            CuentaNoEncontradaException.class,
            UsuarioNoEncontradoException.class
    })
    public ResponseEntity<GenericResponse<Void>> handleNotFoundExceptions(RuntimeException ex) {
        return response(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PagoException.class)
    public ResponseEntity<GenericResponse<Void>> handlePagoException(PagoException ex) {
        CodigoErrorPago error = ex.getError();
        Map<String, String> detalles = new HashMap<>();
        detalles.put("codigo", String.valueOf(error.getCodigo()));
        detalles.put("error", error.name());
        if (ex.getDetalle() != null) {
            detalles.put("detalle", ex.getDetalle());
        }
        return response(error.getStatus(), error.getMensaje(), detalles);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<GenericResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return response(HttpStatus.FORBIDDEN, "No tiene permisos para acceder a este recurso");
    }

    @ExceptionHandler(UsuarioInactivoException.class)
    public ResponseEntity<GenericResponse<Void>> handleUsuarioInactivoException(UsuarioInactivoException ex) {
        return response(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<GenericResponse<Void>> handleCredencialesInvalidasException(CredencialesInvalidasException ex) {
        return response(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler({
            ContrasenaInvalidaException.class,
            ValidacionNegocioException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<GenericResponse<Void>> handleBadRequestExceptions(RuntimeException ex) {
        return response(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Errores HTTP del proveedor fuera del flujo de pago: nunca se expone ni se registra el mensaje original (puede traer URL o credenciales). */
    @ExceptionHandler(feign.FeignException.class)
    public ResponseEntity<GenericResponse<Void>> handleFeignException(feign.FeignException ex) {
        log.error("Error al comunicarse con GestoPago: status={}", ex.status());
        return response(HttpStatus.BAD_GATEWAY, "No fue posible comunicarse con el proveedor de pagos");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse<Void>> handleGeneralException(Exception ex) {
        log.error("Error interno al procesar la solicitud", ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error interno al procesar la solicitud");
    }

    private ResponseEntity<GenericResponse<Void>> response(HttpStatus status, String mensaje) {
        return response(status, mensaje, null);
    }

    private ResponseEntity<GenericResponse<Void>> response(
            HttpStatus status,
            String mensaje,
            Map<String, String> detalles
    ) {
        return ResponseEntity.status(status).body(errorResponseWriter.create(status, mensaje, detalles));
    }
}
