package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.Cuenta;
import com.proyecto.servicios.entity.EstatusCuenta;
import com.proyecto.servicios.exception.CuentaNoEncontradaException;
import com.proyecto.servicios.exception.ValidacionNegocioException;
import com.proyecto.servicios.mapper.ClienteMapper;
import com.proyecto.servicios.model.request.AbonoCuentaRequest;
import com.proyecto.servicios.model.request.BuscaCuentaRequest;
import com.proyecto.servicios.model.request.EstablecePinRequest;
import com.proyecto.servicios.model.response.CuentaResponse;
import com.proyecto.servicios.model.response.SaldoResponse;
import com.proyecto.servicios.repositorys.CuentaRepository;
import com.proyecto.servicios.service.CuentaService;
import com.proyecto.servicios.service.PinSeguridadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PinSeguridadService pinSeguridad;

    @Override
    @Transactional(readOnly = true)
    public List<CuentaResponse> buscarCuentas(BuscaCuentaRequest request) {
        List<Cuenta> cuentas;
        if (request.getNumeroCuenta() != null && !request.getNumeroCuenta().isBlank()) {
            String numeroCuenta = request.getNumeroCuenta().trim();
            Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                    .orElseThrow(() -> new CuentaNoEncontradaException("No se encontró la cuenta bancaria: " + numeroCuenta));
            cuentas = List.of(cuenta);
        } else if (request.isActivas()) {
            cuentas = cuentaRepository.findByEstatusAndActiveClient(EstatusCuenta.ACTIVA);
        } else {
            cuentas = cuentaRepository.findAll();
        }
        return cuentas.stream().map(clienteMapper::toCuentaResponse).collect(Collectors.toList());
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

    @Override
    @Transactional
    public SaldoResponse abonar(AbonoCuentaRequest request) {
        String numeroCuenta = request.getNumeroCuenta().trim();
        Cuenta cuenta = cuentaRepository.findByNumeroCuentaParaActualizar(numeroCuenta)
                .orElseThrow(() -> new CuentaNoEncontradaException("No se encontró la cuenta bancaria: " + numeroCuenta));
        if (cuenta.getEstatus() != EstatusCuenta.ACTIVA) {
            throw new ValidacionNegocioException("Solo se puede abonar a una cuenta activa");
        }
        cuenta.setSaldo(cuenta.getSaldo().add(request.getMonto()));
        cuentaRepository.save(cuenta);
        return SaldoResponse.builder()
                .numeroCuenta(cuenta.getNumeroCuenta())
                .saldo(cuenta.getSaldo())
                .estatus(cuenta.getEstatus().name())
                .build();
    }

    @Override
    @Transactional
    public void establecerPin(EstablecePinRequest request) {
        String numeroCuenta = request.getNumeroCuenta().trim();
        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new CuentaNoEncontradaException("No se encontró la cuenta bancaria: " + numeroCuenta));

        if (cuenta.getPinAutorizacion() != null) {
            // Cambiar un PIN existente exige el PIN actual y la verificación por correo (segundo factor).
            pinSeguridad.verificarPinActual(cuenta, request.getPinActual());
            pinSeguridad.consumirAutorizacion(numeroCuenta, request.getAutorizacionId());
        }
        cuenta.setPinAutorizacion(passwordEncoder.encode(request.getPinNuevo()));
        cuentaRepository.save(cuenta);
    }
}
