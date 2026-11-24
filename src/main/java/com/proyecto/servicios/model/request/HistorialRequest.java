package com.proyecto.servicios.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Paginación y filtros del historial; viajan en el cuerpo de la solicitud, no en la URL. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialRequest {

    @Min(value = 0, message = "La página no puede ser negativa")
    @Builder.Default
    private Integer pagina = 0;

    @Min(value = 1, message = "El tamaño de página debe estar entre 1 y 50")
    @Max(value = 50, message = "El tamaño de página debe estar entre 1 y 50")
    @Builder.Default
    private Integer tamano = 20;

    /** EXITOSA, RECHAZADA o PENDIENTE. Si se omite se devuelven todas. */
    @Pattern(regexp = "EXITOSA|RECHAZADA|PENDIENTE", message = "El estado debe ser EXITOSA, RECHAZADA o PENDIENTE")
    private String estado;

    /** Rango por día de la compra (zona horaria de la operación), ambos extremos incluidos. Opcionales. */
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
}
