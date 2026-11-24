package com.proyecto.servicios.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Peticiones del "Olvidé mi PIN" y del cambio de PIN en pasos (PIN actual → código por correo → PIN nuevo). */
public final class PinCuentaRequests {

    private PinCuentaRequests() {
    }

    /** "Olvidé mi PIN": solo la cuenta; el PIN temporal llega al correo del titular. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Olvide {
        @NotBlank(message = "El número de cuenta es obligatorio")
        @Pattern(regexp = "^\\d{16}$", message = "El número de cuenta debe contener 16 dígitos")
        private String numeroCuenta;
    }

    /** Paso 1 del cambio: PIN actual, para enviar el código por correo. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IniciarCambio {
        @NotBlank(message = "El número de cuenta es obligatorio")
        @Pattern(regexp = "^\\d{16}$", message = "El número de cuenta debe contener 16 dígitos")
        private String numeroCuenta;

        @NotBlank(message = "El PIN actual es obligatorio")
        @Pattern(regexp = "^\\d{4}$", message = "El PIN actual debe contener exactamente 4 dígitos")
        private String pinActual;
    }

    /** Paso 2 del cambio: código recibido por correo. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificarCambio {
        @NotBlank(message = "El número de cuenta es obligatorio")
        @Pattern(regexp = "^\\d{16}$", message = "El número de cuenta debe contener 16 dígitos")
        private String numeroCuenta;

        @NotBlank(message = "El identificador del desafío es obligatorio")
        @Size(max = 64, message = "El identificador del desafío es inválido")
        private String desafioId;

        @NotBlank(message = "El código es obligatorio")
        @Pattern(regexp = "\\d{6}", message = "El código debe tener 6 dígitos")
        private String codigo;
    }
}
