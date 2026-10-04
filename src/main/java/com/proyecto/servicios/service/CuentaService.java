package com.proyecto.servicios.service;

import com.proyecto.servicios.model.response.CuentaResponse;
import com.proyecto.servicios.model.response.SaldoResponse;

import java.util.List;

public interface CuentaService {

    CuentaResponse obtenerCuentaPorNumero(String numeroCuenta);

    List<CuentaResponse> obtenerCuentasActivas();

    SaldoResponse obtenerSaldoCuenta(String numeroCuenta);
}
