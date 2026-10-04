package com.proyecto.servicios.model.response;

import com.proyecto.servicios.entity.EstatusCuenta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CuentaResponse {
    private Long id;
    private String numeroCuenta;
    private Long clienteId;
    private BigDecimal saldo;
    private EstatusCuenta estatus;
    private OffsetDateTime fechaApertura;
    private OffsetDateTime fechaActualizacion;
}
