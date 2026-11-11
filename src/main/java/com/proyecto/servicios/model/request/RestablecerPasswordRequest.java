package com.proyecto.servicios.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Paso 2 de "Olvidé mi contraseña": código recibido por correo y la contraseña nueva. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestablecerPasswordRequest {

    @NotBlank(message = "El identificador del desafío es obligatorio")
    @Size(max = 64, message = "El identificador del desafío es inválido")
    @Pattern(regexp = "^[A-Za-z0-9\\-]+$", message = "El identificador del desafío es inválido")
    private String desafioId;

    @NotBlank(message = "El código es obligatorio")
    @Pattern(regexp = "\\d{6}", message = "El código debe tener 6 dígitos")
    private String codigo;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(max = PatronesSeguros.PASSWORD_MAX, message = PatronesSeguros.MENSAJE_PASSWORD_MAX)
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{8,}$",
            message = "La contraseña debe contener mínimo 8 caracteres, al menos una mayúscula, una minúscula, un número y un carácter especial"
    )
    private String nuevaPassword;
}
