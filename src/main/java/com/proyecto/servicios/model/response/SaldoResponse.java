package com.proyecto.servicios.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaldoResponse {
    private String numeroCuenta;
    private BigDecimal saldo;
    private String estatus;
}
