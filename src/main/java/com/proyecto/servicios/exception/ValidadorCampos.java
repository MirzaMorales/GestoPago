package com.proyecto.servicios.exception;

import com.proyecto.servicios.model.GenericResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ValidadorCampos {

    private final ApiErrorResponseWriter errorResponseWriter;

    public ValidadorCampos(ApiErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    /**
     * Cuando alguna validación de un DTO (@Valid @RequestBody) falla, Spring lanza
     * MethodArgumentNotValidException. Este método la atrapa y regresa los mensajes
     * de error de cada campo con la estructura uniforme de respuesta.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GenericResponse<Void>> dtoValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errores.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                errores.put(error.getObjectName(), error.getDefaultMessage()));
        return respuesta(errores);
    }

    /** Validaciones a nivel método que lanzan ConstraintViolationException. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<GenericResponse<Void>> constraintValidation(ConstraintViolationException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errores.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return respuesta(errores);
    }

    private ResponseEntity<GenericResponse<Void>> respuesta(Map<String, String> errores) {
        return ResponseEntity.badRequest().body(errorResponseWriter.create(
                HttpStatus.BAD_REQUEST, "Error de validación en la solicitud", errores));
    }
}
