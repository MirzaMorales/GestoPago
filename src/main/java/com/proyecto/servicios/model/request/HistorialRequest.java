package com.proyecto.servicios.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Paginación del historial; viaja en el cuerpo de la solicitud, no en la URL. */
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
}
