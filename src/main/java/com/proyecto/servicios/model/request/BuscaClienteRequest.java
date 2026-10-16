package com.proyecto.servicios.model.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Criterios de búsqueda de clientes. Todos son opcionales y combinables. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuscaClienteRequest {

    @Positive(message = "El ID debe ser positivo")
    private Long id;

    @Pattern(regexp = "(?iu)^[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z0-9]\\d$", message = "Formato de CURP inválido")
    private String curp;

    @Pattern(regexp = "(?iu)^[A-Z&Ñ]{3,4}\\d{6}[A-Z0-9]{3}$", message = "Formato de RFC inválido")
    private String rfc;

    @Size(max = 100, message = "El correo electrónico no puede exceder 100 caracteres")
    private String correo;

    @Size(max = 50, message = "El nombre no puede exceder 50 caracteres")
    private String nombre;

    @Size(max = 50, message = "El apellido no puede exceder 50 caracteres")
    private String apellido;

    private Boolean activo;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    @Pattern(regexp = "^\\d{16}$", message = "El número de cuenta debe contener 16 dígitos")
    private String numeroCuenta;

    /** Banderas de detalle de la respuesta (por defecto true). */
    @Builder.Default
    private boolean incluirDomicilio = true;

    @Builder.Default
    private boolean incluirCuentas = true;

    @Builder.Default
    private boolean incluirUsuario = true;
}
