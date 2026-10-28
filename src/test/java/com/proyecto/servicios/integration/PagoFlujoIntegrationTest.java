package com.proyecto.servicios.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.Cliente;
import com.proyecto.servicios.entity.Cuenta;
import com.proyecto.servicios.entity.Domicilio;
import com.proyecto.servicios.entity.EstatusCuenta;
import com.proyecto.servicios.entity.EstatusTransaccion;
import com.proyecto.servicios.entity.TransaccionPago;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.exception.CodigoErrorPago;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.gestopago.SendTxResponse;
import com.proyecto.servicios.model.request.PagoServicioRequest;
import com.proyecto.servicios.model.response.PagoServicioResponse;
import com.proyecto.servicios.repositorys.ClienteRepository;
import com.proyecto.servicios.repositorys.CuentaRepository;
import com.proyecto.servicios.repositorys.GestoPagoTxLogRepository;
import com.proyecto.servicios.repositorys.TransaccionPagoRepository;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import com.proyecto.servicios.service.ConciliacionService;
import com.proyecto.servicios.service.GestoPagoTxService;
import com.proyecto.servicios.service.Impl.ConciliacionServiceImpl;
import com.proyecto.servicios.service.Impl.PagoServiceImpl;
import com.proyecto.servicios.service.PagoService;
import com.proyecto.servicios.service.PagoTransaccionService;
import com.proyecto.servicios.service.PagoTransaccionService.ResultadoConciliacion;
import com.proyecto.servicios.service.ReglasMontoPago;
import com.proyecto.servicios.service.SendTxPayloadBuilder;
import com.proyecto.servicios.service.TxLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Prueba de integración del flujo de pago con JPA real (H2 en modo PostgreSQL), servicios reales,
 * bloqueos pesimistas y transacciones reales. Solo se simula el proveedor externo (GestoPagoTxService).
 * Cada operación del servicio corre en su propia transacción, igual que en producción.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:pagos_it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PagoServiceImpl.class, PagoTransaccionService.class, ConciliacionServiceImpl.class, TxLogService.class,
        ReglasMontoPago.class, SendTxPayloadBuilder.class, PagoFlujoIntegrationTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PagoFlujoIntegrationTest {

    @TestConfiguration
    static class Config {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @MockBean
    private GestoPagoTxService gestoPago;

    @Autowired
    private PagoService pagoService;

    @Autowired
    private ConciliacionService conciliacionService;

    @Autowired
    private PagoTransaccionService transaccionService;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private TransaccionPagoRepository transaccionRepository;

    @Autowired
    private GestoPagoTxLogRepository logRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String numeroCuenta;

    @BeforeEach
    void preparar() {
        String sufijo = UUID.randomUUID().toString().replace("-", "");
        Cliente cliente = Cliente.builder()
                .nombre("Prueba").apellidoPaterno("Integracion").apellidoMaterno("Pagos")
                .fechaNacimiento(LocalDate.of(1990, 1, 1))
                .curp(sufijo.substring(0, 18)).rfc(sufijo.substring(0, 13)).sexo("F").nacionalidad("Mexicana")
                .estadoCivil("SOLTERA").correo(sufijo.substring(0, 12) + "@example.test").telefonoMovil("5512345678")
                .ocupacion("QA").empresa("Ficticia").ingresoMensual(new BigDecimal("1000.00"))
                .domicilio(Domicilio.builder().calle("Calle").numeroExterior("1").colonia("Centro").municipio("CDMX")
                        .estado("CDMX").codigoPostal("06000").pais("Mexico").build())
                .build();
        cliente = clienteRepository.save(cliente);
        numeroCuenta = String.valueOf(1_000_000_000_000_000L + Math.abs(sufijo.hashCode()) % 1_000_000_000L);
        cuentaRepository.save(Cuenta.builder().numeroCuenta(numeroCuenta).cliente(cliente)
                .saldo(new BigDecimal("500.00")).estatus(EstatusCuenta.ACTIVA)
                .pinAutorizacion(passwordEncoder.encode("1234")).build());

        productoRepository.save(ProductoEntity.builder().idProducto(582).producto("Telcel $100").idServicio(133)
                .servicio("Telcel").idCatTipoServicio(2).tipoFront(1).precio(new BigDecimal("100.00")).build());
        productoRepository.save(ProductoEntity.builder().idProducto(205).producto("CFE").idServicio(76)
                .servicio("CFE").idCatTipoServicio(5).tipoFront(2).precio(new BigDecimal("6.00")).build());
        reset(gestoPago);
    }

    @AfterEach
    void limpiar() {
        logRepository.deleteAll();
        transaccionRepository.deleteAll();
        cuentaRepository.deleteAll();
        clienteRepository.deleteAll();
        productoRepository.deleteAll();
    }

    // ---------- utilidades ----------

    private PagoServicioRequest recarga(String pin, String monto) {
        return PagoServicioRequest.builder().numeroCuenta(numeroCuenta).pin(pin).monto(new BigDecimal(monto))
                .telefonoReferencia("5555555551").idServicio(133).idProducto(582).tipoFront(1).build();
    }

    private PagoServicioRequest servicioCfe(String monto, String referencia) {
        return PagoServicioRequest.builder().numeroCuenta(numeroCuenta).pin("1234").monto(new BigDecimal(monto))
                .telefonoReferencia(referencia).idServicio(76).idProducto(205).tipoFront(2).build();
    }

    private SendTxResponse respuestaGp(int codigo) {
        return SendTxResponse.builder().codigo(codigo).idTx("291676393").numAutorizacion("65765544")
                .comision(new BigDecimal("2.50")).respuestaCruda("<RESPONSE><CODIGO>" + codigo + "</CODIGO></RESPONSE>").build();
    }

    private String llave() {
        return "llave-" + UUID.randomUUID();
    }

    private BigDecimal saldo() {
        return cuentaRepository.findByNumeroCuenta(numeroCuenta).orElseThrow().getSaldo();
    }

    private TransaccionPago ultimaTransaccion() {
        List<TransaccionPago> todas = transaccionRepository.findAll();
        return todas.get(todas.size() - 1);
    }

    private CodigoErrorPago errorDe(PagoServicioRequest request, String llave) {
        return assertThrows(PagoException.class, () -> pagoService.pagarServicio(request, llave)).getError();
    }

    private void envejecerEnvio(TransaccionPago t, long minutos) {
        t.setFechaEnvio(OffsetDateTime.now().minusMinutes(minutos));
        transaccionRepository.save(t);
    }

    // ---------- flujo principal ----------

    @Test
    void pagoExitoso_descuentaConfirmaYGuardaBitacora() {
        when(gestoPago.enviarTx(anyString())).thenReturn(respuestaGp(1));

        PagoServicioResponse r = pagoService.pagarServicio(recarga("1234", "100.00"), llave());

        assertEquals(new BigDecimal("400.00"), r.getSaldoRestante());
        assertEquals(new BigDecimal("400.00"), saldo());
        TransaccionPago t = ultimaTransaccion();
        assertEquals(EstatusTransaccion.EXITOSA, t.getEstatus());
        assertEquals("65765544", t.getGpNumAutorizacion());
        assertEquals(14, t.getUpc().length());
        assertTrue(t.getPayloadEnviado().contains(t.getUpc()));
        var bitacora = logRepository.findByTransaccionIdOrderByFechaAsc(t.getId());
        assertEquals(1, bitacora.size());
        assertEquals("SEND_TX", bitacora.get(0).getOperacion());
        assertEquals("CODIGO_1", bitacora.get(0).getResultado());
    }

    @Test
    void pagoRechazadoPorElProveedor_devuelveElSaldo() {
        when(gestoPago.enviarTx(anyString())).thenReturn(respuestaGp(12));

        assertEquals(CodigoErrorPago.GESTOPAGO_RECHAZADO, errorDe(recarga("1234", "100.00"), llave()));

        assertEquals(new BigDecimal("500.00"), saldo());
        assertEquals(EstatusTransaccion.FALLIDA, ultimaTransaccion().getEstatus());
    }

    @Test
    void montoManipuladoEnRecarga_seRechazaYNoSeDescuentaNada() {
        assertEquals(CodigoErrorPago.MONTO_NO_COINCIDE, errorDe(recarga("1234", "1.00"), llave()));

        assertEquals(new BigDecimal("500.00"), saldo());
        verifyNoInteractions(gestoPago);
        assertEquals(0, transaccionRepository.count());
    }

    @Test
    void pagoDeServicioSobreElLimite_seRechaza() {
        assertEquals(CodigoErrorPago.MONTO_EXCEDE_LIMITE, errorDe(servicioCfe("20000.01", "25720381"), llave()));
        verifyNoInteractions(gestoPago);
    }

    // ---------- resultado incierto y conciliación ----------

    @Test
    void timeout82_retieneYLaConciliacionConfirmTx06LaConfirmaConElMismoPayload() {
        when(gestoPago.enviarTx(anyString())).thenReturn(respuestaGp(82));

        assertEquals(CodigoErrorPago.GESTOPAGO_TIMEOUT, errorDe(recarga("1234", "100.00"), llave()));

        TransaccionPago t = ultimaTransaccion();
        assertEquals(EstatusTransaccion.EN_REVISION, t.getEstatus());
        assertEquals(new BigDecimal("400.00"), saldo());

        // confirmTx no puede ejecutarse antes de 62 s: la tx recién creada todavía no es elegible
        assertEquals(0, conciliacionService.conciliarPendientes());
        verify(gestoPago, never()).confirmarTx(any());

        envejecerEnvio(t, 5);
        when(gestoPago.confirmarTx(anyString())).thenReturn(respuestaGp(6));

        assertEquals(1, conciliacionService.conciliarPendientes());

        verify(gestoPago).confirmarTx(t.getPayloadEnviado());
        verify(gestoPago, times(1)).enviarTx(any());
        TransaccionPago resuelta = transaccionRepository.findById(t.getId()).orElseThrow();
        assertEquals(EstatusTransaccion.EXITOSA, resuelta.getEstatus());
        assertEquals(new BigDecimal("400.00"), saldo());
        assertEquals(2, logRepository.findByTransaccionIdOrderByFechaAsc(t.getId()).size());
    }

    @Test
    void caidaDeRed_laConciliacionConfirmTx70DevuelveElSaldoUnaSolaVez() {
        when(gestoPago.enviarTx(anyString())).thenThrow(new PagoException(CodigoErrorPago.GESTOPAGO_NO_DISPONIBLE));

        assertEquals(CodigoErrorPago.GESTOPAGO_NO_DISPONIBLE, errorDe(recarga("1234", "100.00"), llave()));
        TransaccionPago t = ultimaTransaccion();
        assertEquals(EstatusTransaccion.EN_REVISION, t.getEstatus());
        assertEquals(new BigDecimal("400.00"), saldo());

        envejecerEnvio(t, 5);
        when(gestoPago.confirmarTx(anyString())).thenReturn(respuestaGp(70));

        assertEquals(ResultadoConciliacion.REVERTIDA, conciliacionService.conciliar(t.getId()));
        assertEquals(new BigDecimal("500.00"), saldo());

        // Un segundo intento de conciliar (otra instancia, otro ciclo) no puede reembolsar de nuevo
        assertEquals(ResultadoConciliacion.OMITIDA, conciliacionService.conciliar(t.getId()));
        assertEquals(new BigDecimal("500.00"), saldo());
        assertEquals(EstatusTransaccion.FALLIDA, transaccionRepository.findById(t.getId()).orElseThrow().getEstatus());
    }

    @Test
    void transaccionPendienteObsoleta_porCaidaDeLaAppTrasRetener_tambienSeConcilia() {
        // Simula que la app murió después de retener el saldo y antes de llamar al proveedor
        transaccionService.reservar(recarga("1234", "100.00"), llave());
        TransaccionPago t = ultimaTransaccion();
        assertEquals(EstatusTransaccion.PENDIENTE, t.getEstatus());
        assertEquals(new BigDecimal("400.00"), saldo());

        assertEquals(0, conciliacionService.conciliarPendientes()); // aún reciente: podría estar en vuelo

        envejecerEnvio(t, 5);
        when(gestoPago.confirmarTx(anyString())).thenReturn(respuestaGp(70));

        assertEquals(1, conciliacionService.conciliarPendientes());
        assertEquals(new BigDecimal("500.00"), saldo());
    }

    @Test
    void fueraDeLaVentanaDe24Horas_noSeConsultaConfirmTx_yRequiereResolucionManual() {
        when(gestoPago.enviarTx(anyString())).thenReturn(respuestaGp(82));
        errorDe(recarga("1234", "100.00"), llave());
        TransaccionPago t = ultimaTransaccion();
        envejecerEnvio(t, 25 * 60);

        assertEquals(0, conciliacionService.conciliarPendientes());
        verify(gestoPago, never()).confirmarTx(any());
        assertTrue(conciliacionService.listarPendientes().get(0).isRequiereResolucionManual());

        transaccionService.resolverManual(t.getId(), false, "Aclarado con el proveedor", "admin@example.test");

        assertEquals(new BigDecimal("500.00"), saldo());
        assertEquals(EstatusTransaccion.FALLIDA, transaccionRepository.findById(t.getId()).orElseThrow().getEstatus());
    }

    // ---------- idempotencia y duplicados ----------

    @Test
    void mismaLlave_seCobraUnaSolaVezYLaRepeticionDevuelveLoGuardado() {
        when(gestoPago.enviarTx(anyString())).thenReturn(respuestaGp(1));
        String llave = llave();

        pagoService.pagarServicio(recarga("1234", "100.00"), llave);
        PagoServicioResponse repetida = pagoService.pagarServicio(recarga("1234", "100.00"), llave);

        assertTrue(repetida.isRepetida());
        assertEquals("65765544", repetida.getNumAutorizacion());
        assertEquals(new BigDecimal("400.00"), saldo());
        verify(gestoPago, times(1)).enviarTx(any());
        assertEquals(1, transaccionRepository.count());
    }

    @Test
    void mismaLlaveConOtraSolicitud_seRechaza() {
        when(gestoPago.enviarTx(anyString())).thenReturn(respuestaGp(1));
        String llave = llave();
        pagoService.pagarServicio(recarga("1234", "100.00"), llave);

        assertEquals(CodigoErrorPago.IDEMPOTENCY_KEY_REUTILIZADA, errorDe(servicioCfe("50.00", "25720381"), llave));
        assertEquals(new BigDecimal("400.00"), saldo());
    }

    @Test
    void mismaLlaveTrasUnFallo_exigeUnaLlaveNueva() {
        when(gestoPago.enviarTx(anyString())).thenReturn(respuestaGp(12));
        String llave = llave();
        errorDe(recarga("1234", "100.00"), llave);

        assertEquals(CodigoErrorPago.PAGO_PREVIO_FALLIDO, errorDe(recarga("1234", "100.00"), llave));
        verify(gestoPago, times(1)).enviarTx(any());
    }

    @Test
    void operacionIdenticaMientrasLaAnteriorEstaEnRevision_seBloquea() {
        when(gestoPago.enviarTx(anyString())).thenReturn(respuestaGp(82));
        errorDe(recarga("1234", "100.00"), llave());

        assertEquals(CodigoErrorPago.PAGO_EN_PROCESO, errorDe(recarga("1234", "100.00"), llave()));

        assertEquals(new BigDecimal("400.00"), saldo());
        verify(gestoPago, times(1)).enviarTx(any());
    }

    @Test
    void llaveAusente_seRechazaAntesDeTocarLaCuenta() {
        assertEquals(CodigoErrorPago.IDEMPOTENCY_KEY_INVALIDA, errorDe(recarga("1234", "100.00"), null));
        assertEquals(new BigDecimal("500.00"), saldo());
    }

    // ---------- PIN ----------

    @Test
    void cincoPinIncorrectos_bloqueanLaCuentaAunqueDespuesSeaCorrecto() {
        for (int i = 0; i < 4; i++) {
            assertEquals(CodigoErrorPago.PIN_INCORRECTO, errorDe(recarga("0000", "100.00"), llave()));
        }
        assertEquals(CodigoErrorPago.PIN_BLOQUEADO, errorDe(recarga("0000", "100.00"), llave()));

        assertEquals(CodigoErrorPago.PIN_BLOQUEADO, errorDe(recarga("1234", "100.00"), llave()));
        assertEquals(new BigDecimal("500.00"), saldo());
        verifyNoInteractions(gestoPago);
    }

    @Test
    void pinCorrectoReiniciaElConteoDeIntentosFallidos() {
        when(gestoPago.enviarTx(anyString())).thenReturn(respuestaGp(1));
        for (int i = 0; i < 3; i++) {
            errorDe(recarga("0000", "100.00"), llave());
        }

        pagoService.pagarServicio(recarga("1234", "100.00"), llave());

        assertEquals(0, cuentaRepository.findByNumeroCuenta(numeroCuenta).orElseThrow().getPinIntentosFallidos());
    }

    // ---------- concurrencia ----------

    private <T> List<Object> ejecutarEnParalelo(List<Callable<T>> tareas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tareas.size());
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (Callable<T> tarea : tareas) {
                futuros.add(pool.submit(tarea));
            }
            List<Object> resultados = new ArrayList<>();
            for (Future<T> f : futuros) {
                try {
                    resultados.add(f.get());
                } catch (java.util.concurrent.ExecutionException e) {
                    resultados.add(e.getCause());
                }
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void dosPagosSimultaneosConSaldoParaUno_soloUnoSeProcesa() throws Exception {
        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta).orElseThrow();
        cuenta.setSaldo(new BigDecimal("150.00"));
        cuentaRepository.save(cuenta);
        when(gestoPago.enviarTx(anyString())).thenAnswer(inv -> {
            Thread.sleep(300);
            return respuestaGp(1);
        });

        List<Object> resultados = ejecutarEnParalelo(List.of(
                () -> pagoService.pagarServicio(recarga("1234", "100.00"), llave()),
                () -> pagoService.pagarServicio(servicioCfe("100.00", "25720381"), llave())));

        long exitos = resultados.stream().filter(r -> r instanceof PagoServicioResponse).count();
        long saldoInsuficiente = resultados.stream()
                .filter(r -> r instanceof PagoException p && p.getError() == CodigoErrorPago.SALDO_INSUFICIENTE).count();
        assertEquals(1, exitos, resultados.toString());
        assertEquals(1, saldoInsuficiente, resultados.toString());
        assertEquals(new BigDecimal("50.00"), saldo());
        verify(gestoPago, times(1)).enviarTx(any());
    }

    @Test
    void dobleClicSimultaneoConLaMismaLlave_noCobraDosVeces() throws Exception {
        when(gestoPago.enviarTx(anyString())).thenAnswer(inv -> {
            Thread.sleep(300);
            return respuestaGp(1);
        });
        String llave = llave();

        List<Object> resultados = ejecutarEnParalelo(List.of(
                () -> pagoService.pagarServicio(recarga("1234", "100.00"), llave),
                () -> pagoService.pagarServicio(recarga("1234", "100.00"), llave)));

        long exitos = resultados.stream().filter(r -> r instanceof PagoServicioResponse).count();
        assertTrue(exitos >= 1, resultados.toString());
        assertEquals(new BigDecimal("400.00"), saldo());
        verify(gestoPago, times(1)).enviarTx(any());
        assertEquals(1, transaccionRepository.count());
    }
}
