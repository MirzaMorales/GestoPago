package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.Cliente;
import com.proyecto.servicios.entity.Cuenta;
import com.proyecto.servicios.entity.EstatusCuenta;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.exception.CodigoErrorPago;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.request.PagoServicioRequest;
import com.proyecto.servicios.model.response.PagoValidadoResponse;
import com.proyecto.servicios.repositorys.CuentaRepository;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import com.proyecto.servicios.service.Impl.PagoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class PagoServiceTest {

    private static final String NUMERO = "1234567890123456";

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PagoServiceImpl pagoService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private Cuenta cuentaValida() {
        Cliente cliente = new Cliente();
        cliente.setActivo(true);
        return Cuenta.builder()
                .numeroCuenta(NUMERO)
                .cliente(cliente)
                .saldo(new BigDecimal("500.00"))
                .estatus(EstatusCuenta.ACTIVA)
                .pinAutorizacion("hashPin")
                .build();
    }

    private ProductoEntity producto() {
        return ProductoEntity.builder().idProducto(582).producto("Telcel").idServicio(133).tipoFront(1).build();
    }

    private PagoServicioRequest request() {
        return PagoServicioRequest.builder()
                .numeroCuenta(NUMERO)
                .pin("1234")
                .monto(new BigDecimal("100.00"))
                .telefonoReferencia("5555555551")
                .idServicio(133)
                .idProducto(582)
                .tipoFront(1)
                .build();
    }

    private void prepararEscenarioValido(Cuenta cuenta) {
        when(cuentaRepository.findByNumeroCuenta(NUMERO)).thenReturn(Optional.of(cuenta));
        when(passwordEncoder.matches("1234", "hashPin")).thenReturn(true);
        when(productoRepository.findById(582)).thenReturn(Optional.of(producto()));
    }

    private CodigoErrorPago errorDe(PagoServicioRequest request) {
        return assertThrows(PagoException.class, () -> pagoService.validarPago(request)).getError();
    }

    @Test
    void validarPago_todoCorrecto_devuelveResumen() {
        prepararEscenarioValido(cuentaValida());

        PagoValidadoResponse respuesta = pagoService.validarPago(request());

        assertEquals(NUMERO, respuesta.getNumeroCuenta());
        assertEquals(new BigDecimal("500.00"), respuesta.getSaldoDisponible());
        assertEquals("Telcel", respuesta.getProducto());
    }

    @Test
    void validarPago_cuentaInexistente() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO)).thenReturn(Optional.empty());

        assertEquals(CodigoErrorPago.CUENTA_NO_ENCONTRADA, errorDe(request()));
    }

    @Test
    void validarPago_cuentaNoActiva() {
        Cuenta cuenta = cuentaValida();
        cuenta.setEstatus(EstatusCuenta.BLOQUEADA);
        when(cuentaRepository.findByNumeroCuenta(NUMERO)).thenReturn(Optional.of(cuenta));

        assertEquals(CodigoErrorPago.CUENTA_NO_ACTIVA, errorDe(request()));
    }

    @Test
    void validarPago_clienteInactivo() {
        Cuenta cuenta = cuentaValida();
        cuenta.getCliente().setActivo(false);
        when(cuentaRepository.findByNumeroCuenta(NUMERO)).thenReturn(Optional.of(cuenta));

        assertEquals(CodigoErrorPago.CUENTA_NO_ACTIVA, errorDe(request()));
    }

    @Test
    void validarPago_sinPinConfigurado() {
        Cuenta cuenta = cuentaValida();
        cuenta.setPinAutorizacion(null);
        when(cuentaRepository.findByNumeroCuenta(NUMERO)).thenReturn(Optional.of(cuenta));

        assertEquals(CodigoErrorPago.PIN_NO_CONFIGURADO, errorDe(request()));
    }

    @Test
    void validarPago_pinIncorrecto() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO)).thenReturn(Optional.of(cuentaValida()));
        when(passwordEncoder.matches("1234", "hashPin")).thenReturn(false);

        assertEquals(CodigoErrorPago.PIN_INCORRECTO, errorDe(request()));
    }

    @Test
    void validarPago_productoInexistente() {
        prepararEscenarioValido(cuentaValida());
        when(productoRepository.findById(582)).thenReturn(Optional.empty());

        assertEquals(CodigoErrorPago.PRODUCTO_NO_ENCONTRADO, errorDe(request()));
    }

    @Test
    void validarPago_servicioNoCoincide() {
        prepararEscenarioValido(cuentaValida());
        PagoServicioRequest request = request();
        request.setIdServicio(999);

        assertEquals(CodigoErrorPago.SERVICIO_NO_COINCIDE, errorDe(request));
    }

    @Test
    void validarPago_tipoFrontNoCoincide() {
        prepararEscenarioValido(cuentaValida());
        PagoServicioRequest request = request();
        request.setTipoFront(2);

        assertEquals(CodigoErrorPago.TIPO_FRONT_NO_COINCIDE, errorDe(request));
    }

    @Test
    void validarPago_saldoInsuficiente() {
        prepararEscenarioValido(cuentaValida());
        PagoServicioRequest request = request();
        request.setMonto(new BigDecimal("500.01"));

        assertEquals(CodigoErrorPago.SALDO_INSUFICIENTE, errorDe(request));
    }

    @Test
    void validarPago_montoIgualAlSaldo_esValido() {
        prepararEscenarioValido(cuentaValida());
        PagoServicioRequest request = request();
        request.setMonto(new BigDecimal("500.00"));

        assertDoesNotThrow(() -> pagoService.validarPago(request));
    }
}
