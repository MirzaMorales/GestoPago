package com.proyecto.servicios.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstablecePinRequest {

    @NotBlank(message = "El número de cuenta es obligatorio")
    @Pattern(regexp = "^\\d{16}$", message = "El número de cuenta debe contener 16 dígitos")
    private String numeroCuenta;

    @NotBlank(message = "El PIN nuevo es obligatorio")
    @Pattern(regexp = "^\\d{4}$", message = "El PIN debe contener exactamente 4 dígitos")
    private String pinNuevo;

    /** Obligatorio solo cuando la cuenta ya tiene un PIN configurado. */
    @Pattern(regexp = "^\\d{4}$", message = "El PIN actual debe contener exactamente 4 dígitos")
    private String pinActual;
}
