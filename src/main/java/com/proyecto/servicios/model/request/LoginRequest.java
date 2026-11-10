package com.proyecto.servicios.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo electrónico es inválido")
    @Size(max = 100, message = "El correo electrónico no puede exceder 100 caracteres")
    private String correo;

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;

    /** Plataforma desde la que se inicia sesión: WEB o MOVIL. Si se omite se asume WEB. */
    @Pattern(regexp = "WEB|MOVIL", message = "La plataforma debe ser WEB o MOVIL")
    private String plataforma;

    /** true para cerrar la sesión activa en otra plataforma y abrir esta. */
    private Boolean cerrarSesionAnterior;
}
