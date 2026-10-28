package com.proyecto.servicios.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.controller.ConciliacionController;
import com.proyecto.servicios.controller.CuentaController;
import com.proyecto.servicios.controller.PagoController;
import com.proyecto.servicios.exception.ApiErrorResponseWriter;
import com.proyecto.servicios.exception.CodigoErrorPago;
import com.proyecto.servicios.exception.GlobalExceptionHandler;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.response.PagoServicioResponse;
import com.proyecto.servicios.model.response.SaldoResponse;
import com.proyecto.servicios.service.ConciliacionService;
import com.proyecto.servicios.service.CuentaService;
import com.proyecto.servicios.service.PagoService;
import com.proyecto.servicios.service.PagoTransaccionService.ResultadoConciliacion;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

/**
 * Contrato HTTP de los endpoints de pago con el manejo centralizado de errores (@RestControllerAdvice) real.
 * La autorización por rol/propietario (@PreAuthorize) se cubre en AccesosServiceTest y en el manual de pruebas con la app en ejecución.
 */
class PagoApiWebTest {

    private static final String CUENTA = "1234567890123456";
    private static final String LLAVE = "llave-de-prueba-0001";

    private final PagoService pagoService = mock(PagoService.class);
    private final CuentaService cuentaService = mock(CuentaService.class);
    private final ConciliacionService conciliacionService = mock(ConciliacionService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        PagoController pago = new PagoController();
        ReflectionTestUtils.setField(pago, "pagoService", pagoService);
        CuentaController cuenta = new CuentaController();
        ReflectionTestUtils.setField(cuenta, "cuentaService", cuentaService);
        ConciliacionController conciliacion = new ConciliacionController();
        ReflectionTestUtils.setField(conciliacion, "conciliacionService", conciliacionService);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(pago, cuenta, conciliacion)
                .setControllerAdvice(new GlobalExceptionHandler(new ApiErrorResponseWriter(new ObjectMapper().findAndRegisterModules())))
                .setValidator(validator)
                .build();
    }

    private String pagoJson(String monto) {
        return "{\"numeroCuenta\":\"" + CUENTA + "\",\"pin\":\"1234\",\"monto\":" + monto
                + ",\"telefonoReferencia\":\"5555555551\",\"idServicio\":133,\"idProducto\":582,\"tipoFront\":1}";
    }

    // ---------- POST /pagos/servicio ----------

    @Test
    void pago_valido_respondeOkYPasaLaLlaveDeIdempotenciaAlServicio() throws Exception {
        when(pagoService.pagarServicio(any(), eq(LLAVE))).thenReturn(PagoServicioResponse.builder()
                .idTransaccion(77L).saldoRestante(new BigDecimal("400.00")).numAutorizacion("AUT1").build());

        mvc.perform(post("/pagos/servicio").header("Idempotency-Key", LLAVE)
                        .contentType(MediaType.APPLICATION_JSON).content(pagoJson("100.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Pago realizado correctamente"))
                .andExpect(jsonPath("$.datos.idTransaccion").value(77))
                .andExpect(jsonPath("$.datos.numAutorizacion").value("AUT1"));

        verify(pagoService).pagarServicio(any(), eq(LLAVE));
    }

    @Test
    void pago_sinLlaveDeIdempotencia_llegaNullAlServicioYSeRechazaConMensajeClaro() throws Exception {
        when(pagoService.pagarServicio(any(), eq(null))).thenThrow(new PagoException(CodigoErrorPago.IDEMPOTENCY_KEY_INVALIDA));

        mvc.perform(post("/pagos/servicio").contentType(MediaType.APPLICATION_JSON).content(pagoJson("100.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles.codigo").value("1018"))
                .andExpect(jsonPath("$.detalles.error").value("IDEMPOTENCY_KEY_INVALIDA"));
    }

    @Test
    void pago_conParametrosInvalidos_respondeBadRequestConDetallesYNuncaLlegaAlServicio() throws Exception {
        String invalido = "{\"numeroCuenta\":\"123\",\"pin\":\"12a4\",\"monto\":-5,\"telefonoReferencia\":\"55;DROP\"}";

        mvc.perform(post("/pagos/servicio").header("Idempotency-Key", LLAVE)
                        .contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles.numeroCuenta").exists())
                .andExpect(jsonPath("$.detalles.pin").exists())
                .andExpect(jsonPath("$.detalles.monto").exists())
                .andExpect(jsonPath("$.detalles.telefonoReferencia").exists())
                .andExpect(jsonPath("$.detalles.idServicio").exists())
                .andExpect(jsonPath("$.detalles.idProducto").exists())
                .andExpect(jsonPath("$.detalles.tipoFront").exists());

        verifyNoInteractions(pagoService);
    }

    @Test
    void pago_montoConMasDeDosDecimales_seRechaza() throws Exception {
        mvc.perform(post("/pagos/servicio").header("Idempotency-Key", LLAVE)
                        .contentType(MediaType.APPLICATION_JSON).content(pagoJson("10.123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles.monto").exists());
        verifyNoInteractions(pagoService);
    }

    @Test
    void pago_cuerpoVacioOMalformado_respondeBadRequest() throws Exception {
        mvc.perform(post("/pagos/servicio").contentType(MediaType.APPLICATION_JSON).content("{ no es json"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/pagos/servicio").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(pagoService);
    }

    @Test
    void errores_cadaEscenarioTieneSuEstatusHttpYCodigoPropio() throws Exception {
        Object[][] casos = {
                {CodigoErrorPago.PIN_INCORRECTO, 403}, {CodigoErrorPago.PIN_BLOQUEADO, 423},
                {CodigoErrorPago.SALDO_INSUFICIENTE, 422}, {CodigoErrorPago.CUENTA_NO_ENCONTRADA, 404},
                {CodigoErrorPago.PAGO_EN_PROCESO, 409}, {CodigoErrorPago.MONTO_NO_COINCIDE, 422},
                {CodigoErrorPago.GESTOPAGO_RECHAZADO, 422}, {CodigoErrorPago.GESTOPAGO_NO_DISPONIBLE, 502},
                {CodigoErrorPago.GESTOPAGO_TIMEOUT, 504}, {CodigoErrorPago.GESTOPAGO_DUPLICADO, 409},
                {CodigoErrorPago.PRECIO_NO_DISPONIBLE, 503}, {CodigoErrorPago.FIRMA_NO_CONFIGURADA, 500},
        };
        for (Object[] caso : casos) {
            CodigoErrorPago error = (CodigoErrorPago) caso[0];
            reset(pagoService);
            when(pagoService.pagarServicio(any(), any())).thenThrow(new PagoException(error));

            mvc.perform(post("/pagos/servicio").header("Idempotency-Key", LLAVE)
                            .contentType(MediaType.APPLICATION_JSON).content(pagoJson("100.00")))
                    .andExpect(status().is((int) caso[1]))
                    .andExpect(jsonPath("$.status").value((int) caso[1]))
                    .andExpect(jsonPath("$.detalles.codigo").value(String.valueOf(error.getCodigo())))
                    .andExpect(jsonPath("$.detalles.error").value(error.name()));
        }
    }

    @Test
    void pago_resultadoIncierto_informaElIdDeLaTransaccionEnRevision() throws Exception {
        when(pagoService.pagarServicio(any(), any())).thenThrow(new PagoException(CodigoErrorPago.GESTOPAGO_TIMEOUT,
                "La transacción 77 quedó en revisión y el monto permanece retenido hasta confirmar con GestoPago"));

        mvc.perform(post("/pagos/servicio").header("Idempotency-Key", LLAVE)
                        .contentType(MediaType.APPLICATION_JSON).content(pagoJson("100.00")))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.detalles.detalle").value(containsString("77")));
    }

    // ---------- no se filtra información sensible ----------

    @Test
    void errorDelProveedorConUrlYCredenciales_nuncaSeExponeAlCliente() throws Exception {
        FeignException feign = mock(FeignException.class);
        when(feign.getMessage()).thenReturn("POST https://gestopago.portalventas.net/authenticate/?password=SECRETO123 failed");
        when(feign.status()).thenReturn(500);
        when(pagoService.pagarServicio(any(), any())).thenThrow(feign);

        mvc.perform(post("/pagos/servicio").header("Idempotency-Key", LLAVE)
                        .contentType(MediaType.APPLICATION_JSON).content(pagoJson("100.00")))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(not(containsString("SECRETO123"))))
                .andExpect(content().string(not(containsString("gestopago.portalventas.net"))));
    }

    @Test
    void errorInesperado_respondeMensajeGenericoSinDetallesInternos() throws Exception {
        when(pagoService.pagarServicio(any(), any())).thenThrow(new IllegalStateException("jdbc:postgresql://host password=abc"));

        mvc.perform(post("/pagos/servicio").header("Idempotency-Key", LLAVE)
                        .contentType(MediaType.APPLICATION_JSON).content(pagoJson("100.00")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("jdbc"))));
    }

    // ---------- /cuentas/abono y /cuentas/pin ----------

    @Test
    void abono_validaMontoYCuenta() throws Exception {
        mvc.perform(post("/cuentas/abono").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroCuenta\":\"123\",\"monto\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles.numeroCuenta").exists())
                .andExpect(jsonPath("$.detalles.monto").exists());

        when(cuentaService.abonar(any())).thenReturn(SaldoResponse.builder().numeroCuenta(CUENTA).saldo(new BigDecimal("500.00")).estatus("ACTIVA").build());
        mvc.perform(post("/cuentas/abono").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroCuenta\":\"" + CUENTA + "\",\"monto\":500.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.saldo").value(500.00));
    }

    @Test
    void pin_exigeCuatroDigitos() throws Exception {
        mvc.perform(post("/cuentas/pin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroCuenta\":\"" + CUENTA + "\",\"pinNuevo\":\"12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles.pinNuevo").exists());
        verifyNoInteractions(cuentaService);
    }

    // ---------- /pagos/conciliacion ----------

    @Test
    void conciliacion_resolverExigeAccionYObservacion() throws Exception {
        mvc.perform(post("/pagos/conciliacion/resolver").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idTransaccion\":77}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles.accion").exists())
                .andExpect(jsonPath("$.detalles.observacion").exists());
        verifyNoInteractions(conciliacionService);
    }

    @Test
    void conciliacion_resolverRegistraAlUsuarioAutenticado() throws Exception {
        when(conciliacionService.resolverManual(any(), eq("admin@example.test"))).thenReturn(ResultadoConciliacion.REVERTIDA);

        mvc.perform(post("/pagos/conciliacion/resolver").principal(new UsernamePasswordAuthenticationToken("admin@example.test", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idTransaccion\":77,\"accion\":\"REVERTIR\",\"observacion\":\"No aplicada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.resultado").value("REVERTIDA"));
    }

    @Test
    void conciliacion_resolverTransaccionYaResuelta_respondeConflicto() throws Exception {
        when(conciliacionService.resolverManual(any(), any())).thenThrow(new PagoException(CodigoErrorPago.TRANSACCION_NO_CONCILIABLE));

        mvc.perform(post("/pagos/conciliacion/resolver").principal(new UsernamePasswordAuthenticationToken("admin@example.test", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idTransaccion\":77,\"accion\":\"CONFIRMAR\",\"observacion\":\"x\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void conciliacion_pendientesYEjecutar() throws Exception {
        when(conciliacionService.listarPendientes()).thenReturn(List.of());
        when(conciliacionService.conciliarPendientes()).thenReturn(3);

        mvc.perform(post("/pagos/conciliacion/pendientes")).andExpect(status().isOk());
        mvc.perform(post("/pagos/conciliacion/ejecutar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.transaccionesProcesadas").value(3));
    }
}
