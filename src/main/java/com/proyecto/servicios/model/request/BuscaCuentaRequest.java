package com.proyecto.servicios.model.request;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Criterios de búsqueda de cuentas, opcionales y combinables. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuscaCuentaRequest {

    @Pattern(regexp = "^\\d{16}$", message = "El número de cuenta debe contener 16 dígitos")
    private String numeroCuenta;

    /** true: solo cuentas activas de clientes activos. */
    private boolean activas;
}
