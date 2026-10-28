package com.proyecto.servicios.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagoServicioRequest {

    @NotBlank(message = "El número de cuenta es obligatorio")
    @Pattern(regexp = "^\\d{16}$", message = "El número de cuenta debe contener 16 dígitos")
    private String numeroCuenta;

    @NotBlank(message = "El PIN de autorización es obligatorio")
    @Pattern(regexp = "^\\d{4}$", message = "El PIN debe contener exactamente 4 dígitos")
    private String pin;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    @Digits(integer = 13, fraction = 2, message = "El monto admite máximo 13 enteros y 2 decimales")
    private BigDecimal monto;

    @NotBlank(message = "El teléfono o referencia es obligatorio")
    @Size(max = 100, message = "El teléfono o referencia admite máximo 100 caracteres")
    @Pattern(regexp = "^[A-Za-z0-9#]*$", message = "El teléfono o referencia solo admite letras y números")
    private String telefonoReferencia;

    @NotNull(message = "El idServicio es obligatorio")
    @Positive(message = "El idServicio debe ser mayor a cero")
    private Integer idServicio;

    @NotNull(message = "El idProducto es obligatorio")
    @Positive(message = "El idProducto debe ser mayor a cero")
    private Integer idProducto;

    @NotNull(message = "El tipoFront es obligatorio")
    @Positive(message = "El tipoFront debe ser mayor a cero")
    private Integer tipoFront;
}
