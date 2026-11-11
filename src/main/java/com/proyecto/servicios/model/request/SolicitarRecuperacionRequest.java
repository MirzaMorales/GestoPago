package com.proyecto.servicios.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Paso 1 de "Olvidé mi contraseña": correo de la cuenta. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitarRecuperacionRequest {

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo electrónico es inválido")
    @Size(max = 100, message = "El correo electrónico no puede exceder 100 caracteres")
    @Pattern(regexp = PatronesSeguros.CORREO, message = PatronesSeguros.MENSAJE_CORREO)
    private String correo;
}
