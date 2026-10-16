package com.proyecto.servicios.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Envoltorio uniforme para respuestas exitosas y de error:
 * {timestamp, status, mensaje} + datos (éxito) o detalles (errores de validación).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenericResponse<T> {
    private LocalDateTime timestamp;
    private int status;
    private String mensaje;
    private T datos;
    private Map<String, String> detalles;

    public static <T> GenericResponse<T> ok(HttpStatus status, String mensaje, T datos) {
        return GenericResponse.<T>builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .mensaje(mensaje)
                .datos(datos)
                .build();
    }
}
