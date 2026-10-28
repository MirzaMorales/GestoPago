package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.EstatusTransaccion;
import com.proyecto.servicios.entity.TransaccionPago;
import com.proyecto.servicios.exception.CodigoErrorPago;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.gestopago.SendTxResponse;
import com.proyecto.servicios.model.request.ResolverConciliacionRequest;
import com.proyecto.servicios.model.response.TransaccionPendienteResponse;
import com.proyecto.servicios.repositorys.TransaccionPagoRepository;
import com.proyecto.servicios.service.ConciliacionService;
import com.proyecto.servicios.service.GestoPagoTxService;
import com.proyecto.servicios.service.PagoTransaccionService;
import com.proyecto.servicios.service.PagoTransaccionService.ResultadoConciliacion;
import com.proyecto.servicios.service.TxLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@Service
@Slf4j
public class ConciliacionServiceImpl implements ConciliacionService {

    @Autowired
    private TransaccionPagoRepository transaccionRepository;

    @Autowired
    private PagoTransaccionService transaccionService;

    @Autowired
    private GestoPagoTxService gestoPagoTxService;

    @Autowired
    private TxLogService txLogService;

    /** confirmTx no puede ejecutarse antes de 62 s del sendTx. */
    @Value("${pagos.conciliacion.espera-minima-segundos:70}")
    private long esperaMinimaSegundos;

    /** confirmTx solo está disponible 24 h después del sendTx. */
    @Value("${pagos.conciliacion.ventana-horas:24}")
    private long ventanaHoras;

    /** Una transacción PENDIENTE reciente puede estar en vuelo en otra petición; solo se concilia si es obsoleta. */
    @Value("${pagos.conciliacion.pendiente-obsoleto-segundos:120}")
    private long pendienteObsoletoSegundos;

    @Scheduled(fixedDelayString = "${pagos.conciliacion.intervalo-ms:120000}", initialDelayString = "${pagos.conciliacion.intervalo-ms:120000}")
    public void conciliarAutomaticamente() {
        try {
            int procesadas = conciliarPendientes();
            if (procesadas > 0) {
                log.info("Conciliación automática: {} transacciones procesadas", procesadas);
            }
        } catch (RuntimeException e) {
            log.error("Error en la conciliación automática: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public int conciliarPendientes() {
        OffsetDateTime ahora = OffsetDateTime.now();
        OffsetDateTime desde = ahora.minusHours(ventanaHoras);

        List<Long> ids = new ArrayList<>();
        ids.addAll(transaccionRepository.findIdsParaConciliar(
                EnumSet.of(EstatusTransaccion.EN_REVISION), ahora.minusSeconds(esperaMinimaSegundos), desde));
        ids.addAll(transaccionRepository.findIdsParaConciliar(
                EnumSet.of(EstatusTransaccion.PENDIENTE), ahora.minusSeconds(pendienteObsoletoSegundos), desde));

        ids.forEach(this::conciliar);
        return ids.size();
    }

    @Override
    public ResultadoConciliacion conciliar(Long transaccionId) {
        TransaccionPago transaccion = transaccionRepository.findById(transaccionId)
                .orElseThrow(() -> new PagoException(CodigoErrorPago.TRANSACCION_NO_ENCONTRADA));
        if (transaccion.getEstatus() != EstatusTransaccion.PENDIENTE && transaccion.getEstatus() != EstatusTransaccion.EN_REVISION) {
            return ResultadoConciliacion.OMITIDA;
        }

        String payload = transaccion.getPayloadEnviado();
        if (payload == null) {
            log.warn("La transacción {} no tiene payload guardado; requiere resolución manual", transaccionId);
            return ResultadoConciliacion.SIN_RESULTADO;
        }
        SendTxResponse confirmacion;
        try {
            confirmacion = gestoPagoTxService.confirmarTx(payload);
        } catch (PagoException e) {
            log.warn("confirmTx no disponible para la transacción {}: {}", transaccionId, e.getError());
            bitacora(transaccionId, payload, null, e.getError().name());
            return ResultadoConciliacion.SIN_RESULTADO;
        }
        bitacora(transaccionId, payload, confirmacion.getRespuestaCruda(), "CODIGO_" + confirmacion.getCodigo());
        return transaccionService.aplicarConfirmacion(transaccionId, confirmacion);
    }

    @Override
    public List<TransaccionPendienteResponse> listarPendientes() {
        OffsetDateTime limiteManual = OffsetDateTime.now().minusHours(ventanaHoras);
        return transaccionService.listarSinResolver().stream()
                .map(t -> TransaccionPendienteResponse.builder()
                        .id(t.getId())
                        .numeroCuenta(t.getCuenta().getNumeroCuenta())
                        .idProducto(t.getIdProducto())
                        .idServicio(t.getIdServicio())
                        .monto(t.getMonto())
                        .estatus(t.getEstatus().name())
                        .upc(t.getUpc())
                        .fechaEnvio(t.getFechaEnvio())
                        .detalle(t.getDetalle())
                        // Sin payload guardado (operaciones anteriores a la conciliación) o fuera de las 24 h de confirmTx
                        .requiereResolucionManual(t.getPayloadEnviado() == null
                                || t.getFechaEnvio() == null || t.getFechaEnvio().isBefore(limiteManual))
                        .build())
                .toList();
    }

    @Override
    public ResultadoConciliacion resolverManual(ResolverConciliacionRequest request, String usuario) {
        boolean confirmar = request.getAccion() == ResolverConciliacionRequest.Accion.CONFIRMAR;
        log.warn("Resolución manual de la transacción {}: {} por {}", request.getIdTransaccion(), request.getAccion(), usuario);
        return transaccionService.resolverManual(request.getIdTransaccion(), confirmar, request.getObservacion(), usuario);
    }

    private void bitacora(Long id, String payload, String respuesta, String resultado) {
        try {
            txLogService.registrar(id, TxLogService.CONFIRM_TX, payload, respuesta, resultado);
        } catch (RuntimeException e) {
            log.error("No se pudo guardar la bitácora de confirmTx de la transacción {}: {}", id, e.getClass().getSimpleName());
        }
    }
}
