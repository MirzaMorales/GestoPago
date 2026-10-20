package com.proyecto.servicios.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Resultado de la Parte 2: el pago superó las validaciones locales (aún no se envía a GestoPago). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagoValidadoResponse {

    private String numeroCuenta;
    private BigDecimal monto;
    private BigDecimal saldoDisponible;
    private Integer idServicio;
    private Integer idProducto;
    private String producto;
    private Integer tipoFront;
}
