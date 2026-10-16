package com.proyecto.servicios.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteIdRequest {

    @NotNull(message = "El ID del cliente es obligatorio")
    @Positive(message = "El ID debe ser positivo")
    private Long id;
}
