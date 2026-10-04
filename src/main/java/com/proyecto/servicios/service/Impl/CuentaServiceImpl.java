package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.Cuenta;
import com.proyecto.servicios.entity.EstatusCuenta;
import com.proyecto.servicios.exception.CuentaNoEncontradaException;
import com.proyecto.servicios.mapper.ClienteMapper;
import com.proyecto.servicios.model.response.CuentaResponse;
import com.proyecto.servicios.model.response.SaldoResponse;
import com.proyecto.servicios.repositorys.CuentaRepository;
import com.proyecto.servicios.service.CuentaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CuentaServiceImpl implements CuentaService {

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private ClienteMapper clienteMapper;

    @Override
    @Transactional(readOnly = true)
    public CuentaResponse obtenerCuentaPorNumero(String numeroCuenta) {
        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta.trim())
                .orElseThrow(() -> new CuentaNoEncontradaException("No se encontró la cuenta bancaria: " + numeroCuenta));
        return clienteMapper.toCuentaResponse(cuenta);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuentaResponse> obtenerCuentasActivas() {
        return cuentaRepository.findByEstatusAndActiveClient(EstatusCuenta.ACTIVA).stream()
                .map(clienteMapper::toCuentaResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public SaldoResponse obtenerSaldoCuenta(String numeroCuenta) {
        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta.trim())
                .orElseThrow(() -> new CuentaNoEncontradaException("No se encontró la cuenta bancaria: " + numeroCuenta));
        return SaldoResponse.builder()
                .numeroCuenta(cuenta.getNumeroCuenta())
                .saldo(cuenta.getSaldo())
                .estatus(cuenta.getEstatus().name())
                .build();
    }
}
