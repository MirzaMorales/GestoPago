package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.Cuenta;
import com.proyecto.servicios.exception.CuentaNoEncontradaException;
import com.proyecto.servicios.exception.ValidacionNegocioException;
import com.proyecto.servicios.model.request.EstablecePinRequest;
import com.proyecto.servicios.repositorys.CuentaRepository;
import com.proyecto.servicios.service.Impl.CuentaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CuentaServicePinTest {

    private static final String NUMERO = "1234567890123456";

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CuentaServiceImpl cuentaService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private EstablecePinRequest request(String pinNuevo, String pinActual) {
        return EstablecePinRequest.builder().numeroCuenta(NUMERO).pinNuevo(pinNuevo).pinActual(pinActual).build();
    }

    @Test
    void establecerPin_primeraVez_guardaHash() {
        Cuenta cuenta = Cuenta.builder().numeroCuenta(NUMERO).build();
        when(cuentaRepository.findByNumeroCuenta(NUMERO)).thenReturn(Optional.of(cuenta));
        when(passwordEncoder.encode("1234")).thenReturn("hash");

        cuentaService.establecerPin(request("1234", null));

        assertEquals("hash", cuenta.getPinAutorizacion());
        verify(cuentaRepository).save(cuenta);
    }

    @Test
    void establecerPin_conPinExistente_exigePinActualCorrecto() {
        Cuenta cuenta = Cuenta.builder().numeroCuenta(NUMERO).pinAutorizacion("hashViejo").build();
        when(cuentaRepository.findByNumeroCuenta(NUMERO)).thenReturn(Optional.of(cuenta));
        when(passwordEncoder.matches("0000", "hashViejo")).thenReturn(false);

        assertThrows(ValidacionNegocioException.class, () -> cuentaService.establecerPin(request("1234", "0000")));
        assertThrows(ValidacionNegocioException.class, () -> cuentaService.establecerPin(request("1234", null)));
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    void establecerPin_cambioConPinActualCorrecto_actualizaHash() {
        Cuenta cuenta = Cuenta.builder().numeroCuenta(NUMERO).pinAutorizacion("hashViejo").build();
        when(cuentaRepository.findByNumeroCuenta(NUMERO)).thenReturn(Optional.of(cuenta));
        when(passwordEncoder.matches("1111", "hashViejo")).thenReturn(true);
        when(passwordEncoder.encode("2222")).thenReturn("hashNuevo");

        cuentaService.establecerPin(request("2222", "1111"));

        assertEquals("hashNuevo", cuenta.getPinAutorizacion());
    }

    @Test
    void abonar_cuentaActiva_sumaAlSaldo() {
        Cuenta cuenta = Cuenta.builder().numeroCuenta(NUMERO)
                .saldo(new java.math.BigDecimal("100.00")).estatus(com.proyecto.servicios.entity.EstatusCuenta.ACTIVA).build();
        when(cuentaRepository.findByNumeroCuentaParaActualizar(NUMERO)).thenReturn(Optional.of(cuenta));

        var respuesta = cuentaService.abonar(com.proyecto.servicios.model.request.AbonoCuentaRequest.builder()
                .numeroCuenta(NUMERO).monto(new java.math.BigDecimal("250.50")).build());

        assertEquals(new java.math.BigDecimal("350.50"), respuesta.getSaldo());
        assertEquals(new java.math.BigDecimal("350.50"), cuenta.getSaldo());
        verify(cuentaRepository).save(cuenta);
    }

    @Test
    void abonar_cuentaNoActiva_lanzaValidacion() {
        Cuenta cuenta = Cuenta.builder().numeroCuenta(NUMERO)
                .saldo(java.math.BigDecimal.ZERO).estatus(com.proyecto.servicios.entity.EstatusCuenta.BLOQUEADA).build();
        when(cuentaRepository.findByNumeroCuentaParaActualizar(NUMERO)).thenReturn(Optional.of(cuenta));

        assertThrows(ValidacionNegocioException.class, () -> cuentaService.abonar(
                com.proyecto.servicios.model.request.AbonoCuentaRequest.builder()
                        .numeroCuenta(NUMERO).monto(java.math.BigDecimal.TEN).build()));
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    void abonar_cuentaInexistente_lanzaNoEncontrada() {
        when(cuentaRepository.findByNumeroCuentaParaActualizar(NUMERO)).thenReturn(Optional.empty());

        assertThrows(CuentaNoEncontradaException.class, () -> cuentaService.abonar(
                com.proyecto.servicios.model.request.AbonoCuentaRequest.builder()
                        .numeroCuenta(NUMERO).monto(java.math.BigDecimal.TEN).build()));
    }

    @Test
    void establecerPin_cuentaInexistente_lanzaNoEncontrada() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO)).thenReturn(Optional.empty());

        assertThrows(CuentaNoEncontradaException.class, () -> cuentaService.establecerPin(request("1234", null)));
    }
}
