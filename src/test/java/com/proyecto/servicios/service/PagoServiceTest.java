package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.exception.CodigoErrorPago;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.gestopago.SendTxResponse;
import com.proyecto.servicios.model.request.PagoServicioRequest;
import com.proyecto.servicios.model.response.PagoServicioResponse;
import com.proyecto.servicios.service.Impl.PagoServiceImpl;
import com.proyecto.servicios.service.PagoTransaccionService.Reserva;
import com.proyecto.servicios.service.PagoTransaccionService.ResumenPago;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PagoServiceTest {

    private static final String LLAVE = "llave-de-prueba-0001";
    private static final String PAYLOAD = "{\"upc\":\"UPC00000000001\"}";

    @Mock
    private PagoTransaccionService transaccionService;

    @Mock
    private GestoPagoTxService gestoPagoTxService;

    @Mock
    private TxLogService txLogService;

    @InjectMocks
    private PagoServiceImpl pagoService;

    private final ProductoEntity producto =
            ProductoEntity.builder().idProducto(582).producto("Telcel").idServicio(133).tipoFront(1).build();

    private final PagoServicioRequest request = PagoServicioRequest.builder()
            .numeroCuenta("1234567890123456").pin("1234").monto(new BigDecimal("100.00"))
            .telefonoReferencia("5555555551").idServicio(133).idProducto(582).tipoFront(1).build();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(transaccionService.reservar(request, LLAVE)).thenReturn(new Reserva(77L, producto, PAYLOAD, null));
    }

    private SendTxResponse respuesta(int codigo) {
        return SendTxResponse.builder().codigo(codigo).idTx("999").numAutorizacion("AUT1")
                .comision(new BigDecimal("2.50")).fecha("09/11/2010 13:15:26").pin("40919958")
                .leyenda("Texto interno del proveedor").respuestaCruda("<RESPONSE/>").build();
    }

    private PagoException pagarYCapturar() {
        return assertThrows(PagoException.class, () -> pagoService.pagarServicio(request, LLAVE));
    }

    @Test
    void pagarServicio_exitoso_confirmaYDevuelveResumen() {
        SendTxResponse gp = respuesta(1);
        when(gestoPagoTxService.enviarTx(PAYLOAD)).thenReturn(gp);
        when(transaccionService.confirmar(77L, gp)).thenReturn(new BigDecimal("400.00"));

        PagoServicioResponse r = pagoService.pagarServicio(request, LLAVE);

        assertEquals(77L, r.getIdTransaccion());
        assertFalse(r.isRepetida());
        assertEquals(new BigDecimal("400.00"), r.getSaldoRestante());
        assertEquals("AUT1", r.getNumAutorizacion());
        assertEquals("Telcel", r.getProducto());
        assertEquals("40919958", r.getPinProducto());
        verify(txLogService).registrar(eq(77L), eq(TxLogService.SEND_TX), eq(PAYLOAD), eq("<RESPONSE/>"), eq("CODIGO_1"));
        verify(transaccionService, never()).revertir(any(), any());
        verify(transaccionService, never()).marcarEnRevision(any(), any());
    }

    @Test
    void pagarServicio_mismaLlaveYaCompletada_noLlamaAGestoPagoNiCobraDeNuevo() {
        when(transaccionService.reservar(request, LLAVE)).thenReturn(new Reserva(41L, producto, null,
                new ResumenPago(new BigDecimal("100.00"), new BigDecimal("400.00"), "999", "AUT1", new BigDecimal("2.50"))));

        PagoServicioResponse r = pagoService.pagarServicio(request, LLAVE);

        assertTrue(r.isRepetida());
        assertEquals(41L, r.getIdTransaccion());
        assertEquals("AUT1", r.getNumAutorizacion());
        verifyNoInteractions(gestoPagoTxService, txLogService);
    }

    @Test
    void pagarServicio_validacionFalla_noLlamaAGestoPago() {
        when(transaccionService.reservar(request, LLAVE)).thenThrow(new PagoException(CodigoErrorPago.SALDO_INSUFICIENTE));

        assertEquals(CodigoErrorPago.SALDO_INSUFICIENTE, pagarYCapturar().getError());

        verifyNoInteractions(gestoPagoTxService);
    }

    @Test
    void pagarServicio_gestoPagoRechaza_devuelveElSaldoYNoExponeElTextoDelProveedor() {
        when(gestoPagoTxService.enviarTx(PAYLOAD)).thenReturn(respuesta(12));

        PagoException ex = pagarYCapturar();

        assertEquals(CodigoErrorPago.GESTOPAGO_RECHAZADO, ex.getError());
        assertEquals("Código de GestoPago: 12", ex.getDetalle());
        assertFalse(ex.getDetalle().contains("Texto interno"));
        verify(transaccionService).revertir(eq(77L), contains("12"));
        verify(transaccionService, never()).confirmar(any(), any());
    }

    @Test
    void pagarServicio_codigo06Duplicado_devuelveElSaldo() {
        when(gestoPagoTxService.enviarTx(PAYLOAD)).thenReturn(respuesta(6));

        assertEquals(CodigoErrorPago.GESTOPAGO_DUPLICADO, pagarYCapturar().getError());

        verify(transaccionService).revertir(eq(77L), contains("06"));
    }

    @Test
    void pagarServicio_codigo82Timeout_retieneElSaldoYNoReintenta() {
        when(gestoPagoTxService.enviarTx(PAYLOAD)).thenReturn(respuesta(82));

        PagoException ex = pagarYCapturar();

        assertEquals(CodigoErrorPago.GESTOPAGO_TIMEOUT, ex.getError());
        assertTrue(ex.getDetalle().contains("77"));
        verify(transaccionService).marcarEnRevision(eq(77L), contains("82"));
        verify(transaccionService, never()).revertir(any(), any());
        verify(gestoPagoTxService, times(1)).enviarTx(any());
    }

    @Test
    void pagarServicio_firmaOTokenNoDisponible_elProveedorNoRecibioNada_devuelveElSaldo() {
        for (CodigoErrorPago error : new CodigoErrorPago[]{
                CodigoErrorPago.FIRMA_NO_CONFIGURADA, CodigoErrorPago.GESTOPAGO_TOKEN_NO_DISPONIBLE}) {
            reset(transaccionService);
            when(transaccionService.reservar(request, LLAVE)).thenReturn(new Reserva(77L, producto, PAYLOAD, null));
            doThrow(new PagoException(error)).when(gestoPagoTxService).enviarTx(PAYLOAD);

            assertEquals(error, pagarYCapturar().getError());

            verify(transaccionService).revertir(eq(77L), anyString());
            verify(transaccionService, never()).marcarEnRevision(any(), any());
        }
    }

    @Test
    void pagarServicio_fallaDeRedORespuestaIlegible_resultadoIncierto_retieneElSaldo() {
        for (CodigoErrorPago error : new CodigoErrorPago[]{
                CodigoErrorPago.GESTOPAGO_NO_DISPONIBLE, CodigoErrorPago.GESTOPAGO_RESPUESTA_INVALIDA}) {
            reset(transaccionService);
            when(transaccionService.reservar(request, LLAVE)).thenReturn(new Reserva(77L, producto, PAYLOAD, null));
            doThrow(new PagoException(error)).when(gestoPagoTxService).enviarTx(PAYLOAD);

            PagoException ex = pagarYCapturar();

            assertEquals(error, ex.getError());
            assertTrue(ex.getDetalle().contains("77"));
            verify(transaccionService).marcarEnRevision(eq(77L), anyString());
            verify(transaccionService, never()).revertir(any(), any());
        }
    }

    @Test
    void pagarServicio_errorInesperado_marcaEnRevisionYRelanza() {
        when(gestoPagoTxService.enviarTx(PAYLOAD)).thenThrow(new IllegalStateException("boom"));

        assertThrows(IllegalStateException.class, () -> pagoService.pagarServicio(request, LLAVE));

        verify(transaccionService).marcarEnRevision(eq(77L), anyString());
        verify(transaccionService, never()).revertir(any(), any());
    }

    @Test
    void pagarServicio_unFalloDeLaBitacoraNoInterrumpeElPago() {
        SendTxResponse gp = respuesta(1);
        when(gestoPagoTxService.enviarTx(PAYLOAD)).thenReturn(gp);
        when(transaccionService.confirmar(77L, gp)).thenReturn(new BigDecimal("400.00"));
        doThrow(new IllegalStateException("bd caída")).when(txLogService).registrar(any(), any(), any(), any(), any());

        PagoServicioResponse r = pagoService.pagarServicio(request, LLAVE);

        assertEquals(new BigDecimal("400.00"), r.getSaldoRestante());
    }
}
