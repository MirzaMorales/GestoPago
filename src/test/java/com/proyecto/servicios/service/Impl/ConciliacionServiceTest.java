package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.Cuenta;
import com.proyecto.servicios.entity.EstatusTransaccion;
import com.proyecto.servicios.entity.TransaccionPago;
import com.proyecto.servicios.exception.CodigoErrorPago;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.gestopago.SendTxResponse;
import com.proyecto.servicios.model.request.ResolverConciliacionRequest;
import com.proyecto.servicios.model.response.TransaccionPendienteResponse;
import com.proyecto.servicios.repositorys.TransaccionPagoRepository;
import com.proyecto.servicios.service.GestoPagoTxService;
import com.proyecto.servicios.service.PagoTransaccionService;
import com.proyecto.servicios.service.PagoTransaccionService.ResultadoConciliacion;
import com.proyecto.servicios.service.TxLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ConciliacionServiceTest {

    private static final String PAYLOAD = "{\"upc\":\"UPC00000000001\",\"horaLocal\":\"20260101120000\"}";

    @Mock
    private TransaccionPagoRepository transaccionRepository;

    @Mock
    private PagoTransaccionService transaccionService;

    @Mock
    private GestoPagoTxService gestoPagoTxService;

    @Mock
    private TxLogService txLogService;

    @InjectMocks
    private ConciliacionServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(service, "esperaMinimaSegundos", 70L);
        ReflectionTestUtils.setField(service, "ventanaHoras", 24L);
        ReflectionTestUtils.setField(service, "pendienteObsoletoSegundos", 120L);
    }

    private TransaccionPago transaccion(EstatusTransaccion estatus) {
        TransaccionPago t = TransaccionPago.builder().id(77L).estatus(estatus).payloadEnviado(PAYLOAD)
                .cuenta(Cuenta.builder().numeroCuenta("1234567890123456").build())
                .idProducto(582).idServicio(133).monto(new BigDecimal("100.00")).upc("UPC00000000001")
                .fechaEnvio(OffsetDateTime.now().minusMinutes(5)).build();
        when(transaccionRepository.findById(77L)).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    void conciliar_usaConfirmTxConElPayloadOriginalSinCambios() {
        transaccion(EstatusTransaccion.EN_REVISION);
        SendTxResponse confirmacion = SendTxResponse.builder().codigo(6).respuestaCruda("<R/>").build();
        when(gestoPagoTxService.confirmarTx(PAYLOAD)).thenReturn(confirmacion);
        when(transaccionService.aplicarConfirmacion(77L, confirmacion)).thenReturn(ResultadoConciliacion.CONFIRMADA);

        assertEquals(ResultadoConciliacion.CONFIRMADA, service.conciliar(77L));

        verify(gestoPagoTxService).confirmarTx(PAYLOAD);
        verify(gestoPagoTxService, never()).enviarTx(any());
        verify(txLogService).registrar(eq(77L), eq(TxLogService.CONFIRM_TX), eq(PAYLOAD), eq("<R/>"), eq("CODIGO_6"));
    }

    @Test
    void conciliar_transaccionYaResuelta_seOmiteSinLlamarAlProveedor() {
        transaccion(EstatusTransaccion.EXITOSA);

        assertEquals(ResultadoConciliacion.OMITIDA, service.conciliar(77L));

        verifyNoInteractions(gestoPagoTxService);
    }

    @Test
    void conciliar_confirmTxNoDisponible_dejaLaTransaccionSinTocarParaReintentarDespues() {
        transaccion(EstatusTransaccion.EN_REVISION);
        when(gestoPagoTxService.confirmarTx(PAYLOAD)).thenThrow(new PagoException(CodigoErrorPago.GESTOPAGO_NO_DISPONIBLE));

        assertEquals(ResultadoConciliacion.SIN_RESULTADO, service.conciliar(77L));

        verify(transaccionService, never()).aplicarConfirmacion(any(), any());
        verify(txLogService).registrar(eq(77L), eq(TxLogService.CONFIRM_TX), eq(PAYLOAD), eq(null), eq("GESTOPAGO_NO_DISPONIBLE"));
    }

    @Test
    void conciliar_sinPayloadGuardado_noLlamaAlProveedorYRequiereResolucionManual() {
        TransaccionPago t = transaccion(EstatusTransaccion.EN_REVISION);
        t.setPayloadEnviado(null);

        assertEquals(ResultadoConciliacion.SIN_RESULTADO, service.conciliar(77L));

        verifyNoInteractions(gestoPagoTxService);
    }

    @Test
    void conciliar_transaccionInexistente_lanzaNoEncontrada() {
        when(transaccionRepository.findById(9L)).thenReturn(Optional.empty());

        assertEquals(CodigoErrorPago.TRANSACCION_NO_ENCONTRADA,
                assertThrows(PagoException.class, () -> service.conciliar(9L)).getError());
    }

    @Test
    void conciliarPendientes_procesaEnRevisionYPendientesObsoletos() {
        when(transaccionRepository.findIdsParaConciliar(anyCollection(), any(), any()))
                .thenReturn(List.of(77L))      // EN_REVISION
                .thenReturn(List.of(78L));     // PENDIENTE obsoleto
        transaccion(EstatusTransaccion.EN_REVISION);
        TransaccionPago otra = TransaccionPago.builder().id(78L).estatus(EstatusTransaccion.PENDIENTE).payloadEnviado(PAYLOAD).build();
        when(transaccionRepository.findById(78L)).thenReturn(Optional.of(otra));
        when(gestoPagoTxService.confirmarTx(PAYLOAD)).thenReturn(SendTxResponse.builder().codigo(70).build());

        int procesadas = service.conciliarPendientes();

        assertEquals(2, procesadas);
        verify(transaccionService).aplicarConfirmacion(eq(77L), any());
        verify(transaccionService).aplicarConfirmacion(eq(78L), any());
    }

    @Test
    void listarPendientes_marcaLasQueYaSalieronDeLaVentanaDe24Horas() {
        TransaccionPago reciente = TransaccionPago.builder().id(1L).estatus(EstatusTransaccion.EN_REVISION)
                .cuenta(Cuenta.builder().numeroCuenta("1111111111111111").build()).payloadEnviado(PAYLOAD)
                .monto(BigDecimal.TEN).fechaEnvio(OffsetDateTime.now().minusHours(2)).build();
        TransaccionPago vieja = TransaccionPago.builder().id(2L).estatus(EstatusTransaccion.EN_REVISION)
                .cuenta(Cuenta.builder().numeroCuenta("2222222222222222").build()).payloadEnviado(PAYLOAD)
                .monto(BigDecimal.TEN).fechaEnvio(OffsetDateTime.now().minusHours(30)).build();
        TransaccionPago sinPayload = TransaccionPago.builder().id(3L).estatus(EstatusTransaccion.EN_REVISION)
                .cuenta(Cuenta.builder().numeroCuenta("3333333333333333").build()).monto(BigDecimal.TEN).build();
        when(transaccionService.listarSinResolver()).thenReturn(List.of(reciente, vieja, sinPayload));

        List<TransaccionPendienteResponse> lista = service.listarPendientes();

        assertFalse(lista.get(0).isRequiereResolucionManual());
        assertTrue(lista.get(1).isRequiereResolucionManual());
        assertTrue(lista.get(2).isRequiereResolucionManual(), "sin payload guardado no se puede usar confirmTx");
    }

    @Test
    void resolverManual_traduceLaAccionYRegistraAlUsuario() {
        when(transaccionService.resolverManual(77L, true, "Verificado", "admin@example.test"))
                .thenReturn(ResultadoConciliacion.CONFIRMADA);

        ResultadoConciliacion r = service.resolverManual(ResolverConciliacionRequest.builder()
                .idTransaccion(77L).accion(ResolverConciliacionRequest.Accion.CONFIRMAR).observacion("Verificado").build(),
                "admin@example.test");

        assertEquals(ResultadoConciliacion.CONFIRMADA, r);
    }
}
