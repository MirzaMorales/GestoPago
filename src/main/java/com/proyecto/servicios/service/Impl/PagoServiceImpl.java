package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.exception.CodigoErrorPago;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.gestopago.SendTxResponse;
import com.proyecto.servicios.model.request.PagoServicioRequest;
import com.proyecto.servicios.model.response.PagoServicioResponse;
import com.proyecto.servicios.service.GestoPagoTxService;
import com.proyecto.servicios.service.PagoService;
import com.proyecto.servicios.service.PagoTransaccionService;
import com.proyecto.servicios.service.PagoTransaccionService.Reserva;
import com.proyecto.servicios.service.TxLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Orquesta el pago sin una transacción de BD global: la llamada HTTP a GestoPago se hace
 * entre las transacciones cortas de {@link PagoTransaccionService}.
 * <p>
 * Estrategia: retener (descontar) primero, enviar después y resolver según el resultado.
 * Un fallo de red, un timeout o un CODIGO 82 NO significan que el proveedor no procesó el pago:
 * en esos casos el monto queda retenido y la transacción en revisión hasta conciliarla con confirmTx.
 * Nunca se reenvía sendTx automáticamente.
 */
@Service
@Slf4j
public class PagoServiceImpl implements PagoService {

    /** Errores tras los que no se sabe si GestoPago procesó el pago: no se devuelve el saldo. */
    private static final Set<CodigoErrorPago> RESULTADO_INCIERTO = Set.of(
            CodigoErrorPago.GESTOPAGO_NO_DISPONIBLE,
            CodigoErrorPago.GESTOPAGO_RESPUESTA_INVALIDA);

    @Autowired
    private PagoTransaccionService transaccionService;

    @Autowired
    private GestoPagoTxService gestoPagoTxService;

    @Autowired
    private TxLogService txLogService;

    @Override
    public PagoServicioResponse pagarServicio(PagoServicioRequest request, String idempotencyKey) {
        Reserva reserva = transaccionService.reservar(request, idempotencyKey);
        if (reserva.esRepeticion()) {
            return respuestaDeRepeticion(request, reserva);
        }

        Long id = reserva.transaccionId();
        SendTxResponse respuesta;
        try {
            respuesta = gestoPagoTxService.enviarTx(reserva.payloadJson());
        } catch (PagoException e) {
            bitacora(id, reserva.payloadJson(), null, e.getError().name());
            if (RESULTADO_INCIERTO.contains(e.getError())) {
                log.error("Resultado incierto en transacción {}: {}", id, e.getError());
                transaccionService.marcarEnRevision(id, "sendTx: " + e.getError().name() + "; pendiente de confirmTx");
                throw new PagoException(e.getError(), detalleEnRevision(id));
            }
            // Fallo previo al envío (token, firma): el proveedor no recibió nada, se devuelve el saldo
            transaccionService.revertir(id, e.getError().name());
            throw e;
        } catch (RuntimeException e) {
            log.error("Error inesperado en transacción {}: {}", id, e.getClass().getSimpleName());
            bitacora(id, reserva.payloadJson(), null, "ERROR_INESPERADO");
            transaccionService.marcarEnRevision(id, "sendTx: error inesperado; pendiente de confirmTx");
            throw e;
        }

        Integer codigo = respuesta.getCodigo();
        bitacora(id, reserva.payloadJson(), respuesta.getRespuestaCruda(), "CODIGO_" + codigo);

        if (respuesta.isExitoso()) {
            BigDecimal saldoRestante = transaccionService.confirmar(id, respuesta);
            return respuestaExitosa(request, reserva, respuesta, saldoRestante);
        }
        if (respuesta.esTimeout()) {
            transaccionService.marcarEnRevision(id, "sendTx: tiempo de espera (82); pendiente de confirmTx");
            throw new PagoException(CodigoErrorPago.GESTOPAGO_TIMEOUT, detalleEnRevision(id));
        }
        if (Integer.valueOf(SendTxResponse.CODIGO_DUPLICADO_O_APLICADA).equals(codigo)) {
            transaccionService.revertir(id, "GestoPago: operación aplicada anteriormente (código 06)");
            throw new PagoException(CodigoErrorPago.GESTOPAGO_DUPLICADO);
        }
        // Rechazo definitivo: el texto del proveedor queda solo en la bitácora; al cliente solo se le informa el código
        transaccionService.revertir(id, "GestoPago rechazó la transacción (código " + codigo + ")");
        throw new PagoException(CodigoErrorPago.GESTOPAGO_RECHAZADO, "Código de GestoPago: " + codigo);
    }

    private String detalleEnRevision(Long id) {
        return "La transacción " + id + " quedó en revisión y el monto permanece retenido hasta confirmar con GestoPago";
    }

    /** La bitácora nunca debe interrumpir el flujo financiero. */
    private void bitacora(Long id, String payload, String respuesta, String resultado) {
        try {
            txLogService.registrar(id, TxLogService.SEND_TX, payload, respuesta, resultado);
        } catch (RuntimeException e) {
            log.error("No se pudo guardar la bitácora de la transacción {}: {}", id, e.getClass().getSimpleName());
        }
    }

    private PagoServicioResponse respuestaExitosa(PagoServicioRequest request, Reserva reserva, SendTxResponse respuesta, BigDecimal saldoRestante) {
        return PagoServicioResponse.builder()
                .idTransaccion(reserva.transaccionId())
                .numeroCuenta(request.getNumeroCuenta())
                .monto(request.getMonto())
                .saldoRestante(saldoRestante)
                .idServicio(reserva.producto().getIdServicio())
                .idProducto(reserva.producto().getIdProducto())
                .producto(reserva.producto().getProducto())
                .referencia(request.getTelefonoReferencia())
                .comision(respuesta.getComision())
                .idTxGestoPago(respuesta.getIdTx())
                .numAutorizacion(respuesta.getNumAutorizacion())
                .fecha(respuesta.getFecha())
                .pinProducto(respuesta.getPin())
                .leyenda(respuesta.getLeyenda())
                .build();
    }

    /** Misma Idempotency-Key de una operación ya completada: se devuelve lo guardado, sin volver a cobrar ni a llamar a GestoPago. */
    private PagoServicioResponse respuestaDeRepeticion(PagoServicioRequest request, Reserva reserva) {
        var previo = reserva.repeticion();
        return PagoServicioResponse.builder()
                .idTransaccion(reserva.transaccionId())
                .repetida(true)
                .numeroCuenta(request.getNumeroCuenta())
                .monto(previo.monto())
                .saldoRestante(previo.saldoRestante())
                .idServicio(request.getIdServicio())
                .idProducto(request.getIdProducto())
                .producto(reserva.producto() != null ? reserva.producto().getProducto() : null)
                .referencia(request.getTelefonoReferencia())
                .comision(previo.gpComision())
                .idTxGestoPago(previo.gpIdTx())
                .numAutorizacion(previo.gpNumAutorizacion())
                .build();
    }
}
