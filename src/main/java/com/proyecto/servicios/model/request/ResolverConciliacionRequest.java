package com.proyecto.servicios.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolverConciliacionRequest {

    public enum Accion { CONFIRMAR, REVERTIR }

    @NotNull(message = "El id de la transacción es obligatorio")
    private Long idTransaccion;

    @NotNull(message = "La acción es obligatoria (CONFIRMAR o REVERTIR)")
    private Accion accion;

    @NotBlank(message = "La observación es obligatoria")
    @Size(max = 200, message = "La observación admite máximo 200 caracteres")
    private String observacion;
}
