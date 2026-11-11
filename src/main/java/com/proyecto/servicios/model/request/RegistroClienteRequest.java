package com.proyecto.servicios.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistroClienteRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
    @Pattern(regexp = "^[\\p{L}\\p{M}\\s.'’\\-]+$", message = "El nombre contiene caracteres inválidos")
    private String nombre;

    @Size(max = 50, message = "El segundo nombre no puede exceder 50 caracteres")
    @Pattern(regexp = "^[\\p{L}\\p{M}\\s.'’\\-]*$", message = "El segundo nombre contiene caracteres inválidos")
    private String segundoNombre;

    @NotBlank(message = "El apellido paterno es obligatorio")
    @Size(min = 2, max = 50, message = "El apellido paterno debe tener entre 2 y 50 caracteres")
    @Pattern(regexp = "^[\\p{L}\\p{M}\\s.'’\\-]+$", message = "El apellido paterno contiene caracteres inválidos")
    private String apellidoPaterno;

    @NotBlank(message = "El apellido materno es obligatorio")
    @Size(min = 2, max = 50, message = "El apellido materno debe tener entre 2 y 50 caracteres")
    @Pattern(regexp = "^[\\p{L}\\p{M}\\s.'’\\-]+$", message = "El apellido materno contiene caracteres inválidos")
    private String apellidoMaterno;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @Past(message = "La fecha de nacimiento debe ser una fecha pasada")
    private LocalDate fechaNacimiento;

    @NotBlank(message = "La CURP es obligatoria")
    @Pattern(regexp = "(?iu)^[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z0-9]\\d$", message = "Formato de CURP inválido")
    private String curp;

    @NotBlank(message = "El RFC es obligatorio")
    @Pattern(regexp = "(?iu)^[A-Z&Ñ]{3,4}\\d{6}[A-Z0-9]{3}$", message = "Formato de RFC inválido")
    private String rfc;

    @NotBlank(message = "El sexo es obligatorio")
    @Size(max = 20, message = "El sexo no puede exceder 20 caracteres")
    @Pattern(regexp = PatronesSeguros.SOLO_LETRAS, message = PatronesSeguros.MENSAJE_SOLO_LETRAS)
    private String sexo;

    /** Por ahora el servicio es solo para personas de nacionalidad mexicana (la CURP también lo exige). */
    @Size(max = 50, message = "La nacionalidad no puede exceder 50 caracteres")
    @Pattern(regexp = "(?iu)^mexican[ao]$", message = "Por ahora solo se admiten personas de nacionalidad mexicana")
    private String nacionalidad;

    @NotBlank(message = "El estado civil es obligatorio")
    @Size(max = 30, message = "El estado civil no puede exceder 30 caracteres")
    @Pattern(regexp = PatronesSeguros.SOLO_LETRAS, message = PatronesSeguros.MENSAJE_SOLO_LETRAS)
    private String estadoCivil;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo electrónico es inválido")
    @Size(max = 100, message = "El correo electrónico no puede exceder 100 caracteres")
    @Pattern(regexp = PatronesSeguros.CORREO, message = PatronesSeguros.MENSAJE_CORREO)
    private String correo;

    @NotBlank(message = "El teléfono móvil es obligatorio")
    @Pattern(regexp = "^\\d{10}$", message = "El teléfono móvil debe contener exactamente 10 dígitos")
    private String telefonoMovil;

    @Pattern(regexp = "^\\d{10}$", message = "El teléfono alternativo debe contener exactamente 10 dígitos")
    private String telefonoAlternativo;

    @NotBlank(message = "La ocupación es obligatoria")
    @Size(max = 100, message = "La ocupación no puede exceder 100 caracteres")
    @Pattern(regexp = PatronesSeguros.TEXTO_LIBRE, message = PatronesSeguros.MENSAJE_TEXTO_LIBRE)
    private String ocupacion;

    @NotBlank(message = "La empresa es obligatoria")
    @Size(max = 100, message = "La empresa no puede exceder 100 caracteres")
    @Pattern(regexp = PatronesSeguros.TEXTO_LIBRE, message = PatronesSeguros.MENSAJE_TEXTO_LIBRE)
    private String empresa;

    @NotNull(message = "El ingreso mensual es obligatorio")
    @DecimalMin(value = "0.01", message = "El ingreso mensual debe ser mayor a cero")
    @Digits(integer = 13, fraction = 2, message = "El ingreso mensual admite hasta 13 enteros y 2 decimales")
    private BigDecimal ingresoMensual;

    @Valid
    @NotNull(message = "Los datos del domicilio son obligatorios")
    private DomicilioRequest domicilio;

    @NotBlank(message = "La contraseña es obligatoria")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{8,}$",
            message = "La contraseña debe contener mínimo 8 caracteres, al menos una mayúscula, una minúscula, un número y un carácter especial"
    )
    @Size(max = PatronesSeguros.PASSWORD_MAX, message = PatronesSeguros.MENSAJE_PASSWORD_MAX)
    private String password;

    /** Consentimiento expreso: Términos y Condiciones y Aviso de Privacidad. */
    @NotNull(message = "Debes aceptar los Términos y Condiciones y el Aviso de Privacidad")
    @AssertTrue(message = "Debes aceptar los Términos y Condiciones y el Aviso de Privacidad")
    private Boolean aceptaTerminos;

    /** Versión de los documentos que el usuario leyó; debe ser la vigente en el servidor. */
    @NotBlank(message = "La versión de los términos aceptados es obligatoria")
    @Size(max = 20, message = "La versión de los términos no puede exceder 20 caracteres")
    private String versionTerminos;
}
