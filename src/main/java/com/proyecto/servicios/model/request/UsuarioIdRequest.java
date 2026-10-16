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
public class UsuarioIdRequest {

    @NotNull(message = "El ID del usuario es obligatorio")
    @Positive(message = "El ID debe ser positivo")
    private Long id;
}
