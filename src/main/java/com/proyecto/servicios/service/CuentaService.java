package com.proyecto.servicios.service;

import com.proyecto.servicios.model.request.BuscaCuentaRequest;
import com.proyecto.servicios.model.request.EstablecePinRequest;
import com.proyecto.servicios.model.response.CuentaResponse;
import com.proyecto.servicios.model.response.SaldoResponse;

import java.util.List;

public interface CuentaService {

    List<CuentaResponse> buscarCuentas(BuscaCuentaRequest request);

    SaldoResponse obtenerSaldoCuenta(String numeroCuenta);

    void establecerPin(EstablecePinRequest request);
}
