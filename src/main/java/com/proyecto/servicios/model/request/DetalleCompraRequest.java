package com.proyecto.servicios.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Identificador de la compra a consultar; viaja en el cuerpo, no en la URL. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetalleCompraRequest {

    @NotNull(message = "El identificador de la transacción es obligatorio")
    @Positive(message = "El identificador de la transacción es inválido")
    private Long idTransaccion;
}
