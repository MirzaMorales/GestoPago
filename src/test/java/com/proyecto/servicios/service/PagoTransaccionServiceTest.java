package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.Cliente;
import com.proyecto.servicios.entity.Cuenta;
import com.proyecto.servicios.entity.EstatusCuenta;
import com.proyecto.servicios.entity.EstatusTransaccion;
import com.proyecto.servicios.entity.TransaccionPago;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.exception.CodigoErrorPago;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.gestopago.SendTxResponse;
import com.proyecto.servicios.model.request.PagoServicioRequest;
import com.proyecto.servicios.repositorys.CuentaRepository;
import com.proyecto.servicios.repositorys.TransaccionPagoRepository;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import com.proyecto.servicios.service.PagoTransaccionService.Reserva;
import com.proyecto.servicios.service.PagoTransaccionService.ResultadoConciliacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PagoTransaccionServiceTest {

    private static final String NUMERO = "1234567890123456";
    private static final String LLAVE = "llave-de-prueba-0001";

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private TransaccionPagoRepository transaccionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ReglasMontoPago reglasMonto;

    @Mock
    private SendTxPayloadBuilder payloadBuilder;

    @InjectMocks
    private PagoTransaccionService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(service, "pinIntentosMaximos", 5);
        ReflectionTestUtils.setField(service, "pinBloqueoMinutos", 15L);
        when(transaccionRepository.save(any(TransaccionPago.class))).thenAnswer(inv -> {
            TransaccionPago t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(77L);
            }
            return t;
        });
        when(payloadBuilder.generarUpc()).thenReturn("UPC00000000001");
        when(payloadBuilder.construir(any(), any(), any())).thenReturn("{\"payload\":true}");
    }

    private Cuenta cuentaValida() {
        Cliente cliente = new Cliente();
        cliente.setActivo(true);
        return Cuenta.builder()
                .id(5L).numeroCuenta(NUMERO).cliente(cliente)
                .saldo(new BigDecimal("500.00")).estatus(EstatusCuenta.ACTIVA).pinAutorizacion("hashPin")
                .build();
    }

    private ProductoEntity producto() {
        return ProductoEntity.builder().idProducto(582).producto("Telcel").idServicio(133).tipoFront(1).build();
    }

    private PagoServicioRequest request() {
        return PagoServicioRequest.builder()
                .numeroCuenta(NUMERO).pin("1234").monto(new BigDecimal("100.00")).telefonoReferencia("5555555551")
                .idServicio(133).idProducto(582).tipoFront(1).build();
    }

    private void prepararEscenarioValido(Cuenta cuenta) {
        when(cuentaRepository.findByNumeroCuentaParaActualizar(NUMERO)).thenReturn(Optional.of(cuenta));
        when(passwordEncoder.matches("1234", "hashPin")).thenReturn(true);
        when(productoRepository.findById(582)).thenReturn(Optional.of(producto()));
        when(reglasMonto.montoADebitar(any(), any())).thenReturn(new BigDecimal("100.00"));
    }

    private CodigoErrorPago errorDe(PagoServicioRequest request) {
        return assertThrows(PagoException.class, () -> service.reservar(request, LLAVE)).getError();
    }

    private void verificarSinDescuento(Cuenta cuenta) {
        assertEquals(new BigDecimal("500.00"), cuenta.getSaldo());
        verify(transaccionRepository, never()).save(any());
    }

    // ---------- reservar: caso feliz ----------

    @Test
    void reservar_todoCorrecto_descuentaSaldoYRegistraPendienteConUpcPayloadYLlave() {
        Cuenta cuenta = cuentaValida();
        prepararEscenarioValido(cuenta);

        Reserva reserva = service.reservar(request(), LLAVE);

        assertEquals(77L, reserva.transaccionId());
        assertFalse(reserva.esRepeticion());
        assertEquals("{\"payload\":true}", reserva.payloadJson());
        assertEquals(new BigDecimal("400.00"), cuenta.getSaldo());
        ArgumentCaptor<TransaccionPago> captor = ArgumentCaptor.forClass(TransaccionPago.class);
        verify(transaccionRepository).save(captor.capture());
        TransaccionPago t = captor.getValue();
        assertEquals(EstatusTransaccion.PENDIENTE, t.getEstatus());
        assertEquals(new BigDecimal("100.00"), t.getMonto());
        assertEquals(LLAVE, t.getIdempotencyKey());
        assertEquals("UPC00000000001", t.getUpc());
        assertEquals("{\"payload\":true}", t.getPayloadEnviado());
        assertNotNull(t.getFechaEnvio());
        assertEquals(PagoTransaccionService.huella(request()), t.getHuellaSolicitud());
    }

    @Test
    void reservar_montoADebitarLoDefineElCatalogo_noElCliente() {
        Cuenta cuenta = cuentaValida();
        prepararEscenarioValido(cuenta);
        when(reglasMonto.montoADebitar(any(), any())).thenReturn(new BigDecimal("80.00"));

        service.reservar(request(), LLAVE);

        assertEquals(new BigDecimal("420.00"), cuenta.getSaldo());
    }

    // ---------- reservar: llave de idempotencia ----------

    @Test
    void reservar_llaveAusenteOInvalida_seRechazaSinTocarNada() {
        for (String llave : new String[]{null, "", "corta", "con espacios no valen", "x".repeat(65), "llave;DROP-TABLE"}) {
            assertEquals(CodigoErrorPago.IDEMPOTENCY_KEY_INVALIDA,
                    assertThrows(PagoException.class, () -> service.reservar(request(), llave)).getError(), String.valueOf(llave));
        }
        verifyNoInteractions(cuentaRepository);
    }

    @Test
    void reservar_mismaLlaveMismaSolicitudYaExitosa_devuelveLoGuardadoSinCobrarDeNuevo() {
        Cuenta cuenta = cuentaValida();
        cuenta.setSaldo(new BigDecimal("400.00"));
        prepararEscenarioValido(cuenta);
        TransaccionPago previa = TransaccionPago.builder().id(41L).estatus(EstatusTransaccion.EXITOSA)
                .huellaSolicitud(PagoTransaccionService.huella(request())).idProducto(582)
                .monto(new BigDecimal("100.00")).gpIdTx("999").gpNumAutorizacion("AUT1").build();
        when(transaccionRepository.findByCuentaIdAndIdempotencyKey(5L, LLAVE)).thenReturn(Optional.of(previa));

        Reserva reserva = service.reservar(request(), LLAVE);

        assertTrue(reserva.esRepeticion());
        assertEquals(41L, reserva.transaccionId());
        assertEquals("AUT1", reserva.repeticion().gpNumAutorizacion());
        assertEquals(new BigDecimal("400.00"), cuenta.getSaldo());
        verify(cuentaRepository, never()).save(any());
        verify(transaccionRepository, never()).save(any());
    }

    @Test
    void reservar_mismaLlaveConOtraSolicitud_seRechaza() {
        prepararEscenarioValido(cuentaValida());
        TransaccionPago previa = TransaccionPago.builder().id(41L).estatus(EstatusTransaccion.EXITOSA)
                .huellaSolicitud("otra-huella").build();
        when(transaccionRepository.findByCuentaIdAndIdempotencyKey(5L, LLAVE)).thenReturn(Optional.of(previa));

        assertEquals(CodigoErrorPago.IDEMPOTENCY_KEY_REUTILIZADA, errorDe(request()));
    }

    @Test
    void reservar_mismaLlaveYPagoAunSinResolver_respondeEnProceso() {
        for (EstatusTransaccion estatus : new EstatusTransaccion[]{EstatusTransaccion.PENDIENTE, EstatusTransaccion.EN_REVISION}) {
            prepararEscenarioValido(cuentaValida());
            TransaccionPago previa = TransaccionPago.builder().id(41L).estatus(estatus)
                    .huellaSolicitud(PagoTransaccionService.huella(request())).build();
            when(transaccionRepository.findByCuentaIdAndIdempotencyKey(5L, LLAVE)).thenReturn(Optional.of(previa));

            assertEquals(CodigoErrorPago.PAGO_EN_PROCESO, errorDe(request()), estatus.name());
        }
    }

    @Test
    void reservar_mismaLlaveYPagoFallido_pideUnaLlaveNueva() {
        prepararEscenarioValido(cuentaValida());
        TransaccionPago previa = TransaccionPago.builder().id(41L).estatus(EstatusTransaccion.FALLIDA)
                .huellaSolicitud(PagoTransaccionService.huella(request())).build();
        when(transaccionRepository.findByCuentaIdAndIdempotencyKey(5L, LLAVE)).thenReturn(Optional.of(previa));

        assertEquals(CodigoErrorPago.PAGO_PREVIO_FALLIDO, errorDe(request()));
    }

    @Test
    void reservar_operacionIdenticaSinResolverConOtraLlave_seRechaza() {
        Cuenta cuenta = cuentaValida();
        prepararEscenarioValido(cuenta);
        when(transaccionRepository.existsByCuentaIdAndIdProductoAndTelefonoReferenciaAndMontoAndEstatusIn(
                eq(5L), any(), any(), any(), any())).thenReturn(true);

        assertEquals(CodigoErrorPago.PAGO_EN_PROCESO, errorDe(request()));
        verificarSinDescuento(cuenta);
    }

    // ---------- reservar: cuenta, PIN, catálogo, monto, saldo ----------

    @Test
    void reservar_cuentaInexistente() {
        when(cuentaRepository.findByNumeroCuentaParaActualizar(NUMERO)).thenReturn(Optional.empty());

        assertEquals(CodigoErrorPago.CUENTA_NO_ENCONTRADA, errorDe(request()));
    }

    @Test
    void reservar_cuentaNoActivaOClienteInactivo() {
        Cuenta bloqueada = cuentaValida();
        bloqueada.setEstatus(EstatusCuenta.BLOQUEADA);
        when(cuentaRepository.findByNumeroCuentaParaActualizar(NUMERO)).thenReturn(Optional.of(bloqueada));
        assertEquals(CodigoErrorPago.CUENTA_NO_ACTIVA, errorDe(request()));

        Cuenta clienteInactivo = cuentaValida();
        clienteInactivo.getCliente().setActivo(false);
        when(cuentaRepository.findByNumeroCuentaParaActualizar(NUMERO)).thenReturn(Optional.of(clienteInactivo));
        assertEquals(CodigoErrorPago.CUENTA_NO_ACTIVA, errorDe(request()));
        verificarSinDescuento(clienteInactivo);
    }

    @Test
    void reservar_sinPinConfigurado() {
        Cuenta cuenta = cuentaValida();
        cuenta.setPinAutorizacion(null);
        when(cuentaRepository.findByNumeroCuentaParaActualizar(NUMERO)).thenReturn(Optional.of(cuenta));

        assertEquals(CodigoErrorPago.PIN_NO_CONFIGURADO, errorDe(request()));
    }

    @Test
    void reservar_pinIncorrecto_cuentaIntentoFallidoYNoDescuenta() {
        Cuenta cuenta = cuentaValida();
        when(cuentaRepository.findByNumeroCuentaParaActualizar(NUMERO)).thenReturn(Optional.of(cuenta));
        when(passwordEncoder.matches("1234", "hashPin")).thenReturn(false);

        assertEquals(CodigoErrorPago.PIN_INCORRECTO, errorDe(request()));

        assertEquals(1, cuenta.getPinIntentosFallidos());
        assertEquals(new BigDecimal("500.00"), cuenta.getSaldo());
        verify(cuentaRepository).save(cuenta);
        verify(transaccionRepository, never()).save(any());
    }

    @Test
    void reservar_quintoPinIncorrecto_bloqueaLaCuentaTemporalmente() {
        Cuenta cuenta = cuentaValida();
        cuenta.setPinIntentosFallidos(4);
        when(cuentaRepository.findByNumeroCuentaParaActualizar(NUMERO)).thenReturn(Optional.of(cuenta));
        when(passwordEncoder.matches("1234", "hashPin")).thenReturn(false);

        assertEquals(CodigoErrorPago.PIN_BLOQUEADO, errorDe(request()));

        assertEquals(0, cuenta.getPinIntentosFallidos());
        assertTrue(cuenta.getPinBloqueadoHasta().isAfter(OffsetDateTime.now().plusMinutes(14)));
    }

    @Test
    void reservar_cuentaBloqueadaPorPin_noVerificaElPinNiSiquieraElCorrecto() {
        Cuenta cuenta = cuentaValida();
        cuenta.setPinBloqueadoHasta(OffsetDateTime.now().plusMinutes(5));
        when(cuentaRepository.findByNumeroCuentaParaActualizar(NUMERO)).thenReturn(Optional.of(cuenta));

        assertEquals(CodigoErrorPago.PIN_BLOQUEADO, errorDe(request()));

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void reservar_bloqueoExpirado_permiteIntentarYUnPinCorrectoReiniciaElConteo() {
        Cuenta cuenta = cuentaValida();
        cuenta.setPinBloqueadoHasta(OffsetDateTime.now().minusMinutes(1));
        cuenta.setPinIntentosFallidos(3);
        prepararEscenarioValido(cuenta);

        service.reservar(request(), LLAVE);

        assertEquals(0, cuenta.getPinIntentosFallidos());
        assertNull(cuenta.getPinBloqueadoHasta());
    }

    @Test
    void reservar_productoInexistenteServicioOTipoFrontNoCoinciden() {
        Cuenta cuenta = cuentaValida();
        prepararEscenarioValido(cuenta);

        when(productoRepository.findById(582)).thenReturn(Optional.empty());
        assertEquals(CodigoErrorPago.PRODUCTO_NO_ENCONTRADO, errorDe(request()));

        when(productoRepository.findById(582)).thenReturn(Optional.of(producto()));
        PagoServicioRequest otroServicio = request();
        otroServicio.setIdServicio(999);
        assertEquals(CodigoErrorPago.SERVICIO_NO_COINCIDE, errorDe(otroServicio));

        PagoServicioRequest otroTipo = request();
        otroTipo.setTipoFront(2);
        assertEquals(CodigoErrorPago.TIPO_FRONT_NO_COINCIDE, errorDe(otroTipo));
        verificarSinDescuento(cuenta);
    }

    @Test
    void reservar_reglasDeMontoRechazan_noDescuentaNada() {
        Cuenta cuenta = cuentaValida();
        prepararEscenarioValido(cuenta);
        when(reglasMonto.montoADebitar(any(), any())).thenThrow(new PagoException(CodigoErrorPago.MONTO_NO_COINCIDE));

        assertEquals(CodigoErrorPago.MONTO_NO_COINCIDE, errorDe(request()));
        verificarSinDescuento(cuenta);
    }

    @Test
    void reservar_saldoInsuficiente_noDescuentaNada() {
        Cuenta cuenta = cuentaValida();
        cuenta.setSaldo(new BigDecimal("99.99"));
        prepararEscenarioValido(cuenta);

        assertEquals(CodigoErrorPago.SALDO_INSUFICIENTE, errorDe(request()));
        assertEquals(new BigDecimal("99.99"), cuenta.getSaldo());
        verify(transaccionRepository, never()).save(any());
    }

    @Test
    void reservar_montoIgualAlSaldo_dejaSaldoEnCero() {
        Cuenta cuenta = cuentaValida();
        cuenta.setSaldo(new BigDecimal("100.00"));
        prepararEscenarioValido(cuenta);

        service.reservar(request(), LLAVE);

        assertEquals(0, BigDecimal.ZERO.compareTo(cuenta.getSaldo()));
    }

    // ---------- confirmar / revertir / en revisión ----------

    private TransaccionPago transaccion(Cuenta cuenta, EstatusTransaccion estatus) {
        TransaccionPago t = TransaccionPago.builder()
                .id(77L).cuenta(cuenta).monto(new BigDecimal("100.00")).estatus(estatus).build();
        when(transaccionRepository.findById(77L)).thenReturn(Optional.of(t));
        when(transaccionRepository.findByIdParaActualizar(77L)).thenReturn(Optional.of(t));
        when(cuentaRepository.findByNumeroCuentaParaActualizar(NUMERO)).thenReturn(Optional.of(cuenta));
        return t;
    }

    private Cuenta cuentaConSaldo(String saldo) {
        Cuenta cuenta = cuentaValida();
        cuenta.setSaldo(new BigDecimal(saldo));
        return cuenta;
    }

    @Test
    void confirmar_guardaDatosDeGestoPagoYDevuelveSaldo() {
        TransaccionPago t = transaccion(cuentaConSaldo("400.00"), EstatusTransaccion.PENDIENTE);

        BigDecimal saldo = service.confirmar(77L, SendTxResponse.builder()
                .codigo(1).idTx("999").numAutorizacion("AUT1").comision(new BigDecimal("2.50")).build());

        assertEquals(new BigDecimal("400.00"), saldo);
        assertEquals(EstatusTransaccion.EXITOSA, t.getEstatus());
        assertEquals("999", t.getGpIdTx());
        assertEquals("AUT1", t.getGpNumAutorizacion());
        assertNotNull(t.getFechaResolucion());
    }

    @Test
    void confirmar_unaTransaccionYaRevertida_noSePuedeConfirmar() {
        transaccion(cuentaConSaldo("500.00"), EstatusTransaccion.FALLIDA);

        assertEquals(CodigoErrorPago.TRANSACCION_NO_CONCILIABLE,
                assertThrows(PagoException.class, () -> service.confirmar(77L, SendTxResponse.builder().codigo(1).build())).getError());
    }

    @Test
    void revertir_devuelveElMontoYMarcaFallida() {
        Cuenta cuenta = cuentaConSaldo("400.00");
        TransaccionPago t = transaccion(cuenta, EstatusTransaccion.PENDIENTE);

        service.revertir(77L, "GESTOPAGO_RECHAZADO");

        assertEquals(new BigDecimal("500.00"), cuenta.getSaldo());
        assertEquals(EstatusTransaccion.FALLIDA, t.getEstatus());
        assertEquals("GESTOPAGO_RECHAZADO", t.getDetalle());
    }

    @Test
    void revertir_noReembolsaDosVeces_niUnaTransaccionYaExitosa() {
        for (EstatusTransaccion yaResuelta : new EstatusTransaccion[]{EstatusTransaccion.FALLIDA, EstatusTransaccion.EXITOSA}) {
            Cuenta cuenta = cuentaConSaldo("400.00");
            transaccion(cuenta, yaResuelta);

            service.revertir(77L, "otra vez");

            assertEquals(new BigDecimal("400.00"), cuenta.getSaldo(), yaResuelta.name());
        }
    }

    @Test
    void marcarEnRevision_noTocaElSaldoYNoDegradaUnaTransaccionResuelta() {
        Cuenta cuenta = cuentaConSaldo("400.00");
        TransaccionPago t = transaccion(cuenta, EstatusTransaccion.PENDIENTE);
        service.marcarEnRevision(77L, "GESTOPAGO_NO_DISPONIBLE");
        assertEquals(EstatusTransaccion.EN_REVISION, t.getEstatus());
        assertEquals(new BigDecimal("400.00"), cuenta.getSaldo());

        t.setEstatus(EstatusTransaccion.EXITOSA);
        service.marcarEnRevision(77L, "no debe degradar");
        assertEquals(EstatusTransaccion.EXITOSA, t.getEstatus());
    }

    // ---------- conciliación con confirmTx ----------

    @Test
    void aplicarConfirmacion_codigo06_confirmaLaTransaccion() {
        TransaccionPago t = transaccion(cuentaConSaldo("400.00"), EstatusTransaccion.EN_REVISION);

        ResultadoConciliacion r = service.aplicarConfirmacion(77L,
                SendTxResponse.builder().codigo(6).idTx("291437188").numAutorizacion("100024988").build());

        assertEquals(ResultadoConciliacion.CONFIRMADA, r);
        assertEquals(EstatusTransaccion.EXITOSA, t.getEstatus());
        assertEquals("291437188", t.getGpIdTx());
    }

    @Test
    void aplicarConfirmacion_codigo70_devuelveElSaldo() {
        Cuenta cuenta = cuentaConSaldo("400.00");
        TransaccionPago t = transaccion(cuenta, EstatusTransaccion.EN_REVISION);

        ResultadoConciliacion r = service.aplicarConfirmacion(77L, SendTxResponse.builder().codigo(70).build());

        assertEquals(ResultadoConciliacion.REVERTIDA, r);
        assertEquals(EstatusTransaccion.FALLIDA, t.getEstatus());
        assertEquals(new BigDecimal("500.00"), cuenta.getSaldo());
    }

    @Test
    void aplicarConfirmacion_codigoDesconocido_dejaEnRevisionSinMoverDinero() {
        Cuenta cuenta = cuentaConSaldo("400.00");
        TransaccionPago t = transaccion(cuenta, EstatusTransaccion.EN_REVISION);

        ResultadoConciliacion r = service.aplicarConfirmacion(77L, SendTxResponse.builder().codigo(99).build());

        assertEquals(ResultadoConciliacion.SIN_RESULTADO, r);
        assertEquals(EstatusTransaccion.EN_REVISION, t.getEstatus());
        assertEquals(new BigDecimal("400.00"), cuenta.getSaldo());
    }

    @Test
    void aplicarConfirmacion_transaccionYaResuelta_seOmite() {
        Cuenta cuenta = cuentaConSaldo("400.00");
        transaccion(cuenta, EstatusTransaccion.EXITOSA);

        assertEquals(ResultadoConciliacion.OMITIDA, service.aplicarConfirmacion(77L, SendTxResponse.builder().codigo(70).build()));
        assertEquals(new BigDecimal("400.00"), cuenta.getSaldo());
    }

    // ---------- resolución manual ----------

    @Test
    void resolverManual_confirmar_marcaExitosaSinMoverSaldo() {
        Cuenta cuenta = cuentaConSaldo("400.00");
        TransaccionPago t = transaccion(cuenta, EstatusTransaccion.EN_REVISION);

        ResultadoConciliacion r = service.resolverManual(77L, true, "Verificado en el portal del proveedor", "admin@example.test");

        assertEquals(ResultadoConciliacion.CONFIRMADA, r);
        assertEquals(EstatusTransaccion.EXITOSA, t.getEstatus());
        assertTrue(t.getDetalle().contains("admin@example.test"));
        assertEquals(new BigDecimal("400.00"), cuenta.getSaldo());
    }

    @Test
    void resolverManual_revertir_devuelveElSaldo() {
        Cuenta cuenta = cuentaConSaldo("400.00");
        TransaccionPago t = transaccion(cuenta, EstatusTransaccion.EN_REVISION);

        service.resolverManual(77L, false, "No aplicada según el proveedor", "admin@example.test");

        assertEquals(EstatusTransaccion.FALLIDA, t.getEstatus());
        assertEquals(new BigDecimal("500.00"), cuenta.getSaldo());
    }

    @Test
    void resolverManual_transaccionYaResuelta_seRechaza() {
        transaccion(cuentaConSaldo("400.00"), EstatusTransaccion.EXITOSA);

        assertEquals(CodigoErrorPago.TRANSACCION_NO_CONCILIABLE,
                assertThrows(PagoException.class, () -> service.resolverManual(77L, false, "x", "admin")).getError());
    }

    @Test
    void resolver_transaccionInexistente_lanzaNoEncontrada() {
        when(transaccionRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertEquals(CodigoErrorPago.TRANSACCION_NO_ENCONTRADA,
                assertThrows(PagoException.class, () -> service.revertir(1L, "x")).getError());
    }
}
