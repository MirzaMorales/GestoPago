package com.proyecto.servicios.exception;

import com.proyecto.servicios.entity.EstatusTransaccion;
import com.proyecto.servicios.model.EstadoCompra;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EstadoCompraTest {

    @Test
    void erroresDeComunicacionYTecnicos() {
        assertEquals(EstadoCompra.ERROR_COMUNICACION, CodigoErrorPago.GESTOPAGO_NO_DISPONIBLE.estadoCompra());
        assertEquals(EstadoCompra.ERROR_COMUNICACION, CodigoErrorPago.GESTOPAGO_RESPUESTA_INVALIDA.estadoCompra());
        assertEquals(EstadoCompra.ERROR_COMUNICACION, CodigoErrorPago.GESTOPAGO_TOKEN_NO_DISPONIBLE.estadoCompra());
    }

    @Test
    void tiempoDeEsperaYPagoEnProcesoSonPendientes() {
        assertEquals(EstadoCompra.PENDIENTE, CodigoErrorPago.GESTOPAGO_TIMEOUT.estadoCompra());
        assertEquals(EstadoCompra.PENDIENTE, CodigoErrorPago.PAGO_EN_PROCESO.estadoCompra());
    }

    @Test
    void rechazosDefinitivosSonRechazadas() {
        assertEquals(EstadoCompra.RECHAZADA, CodigoErrorPago.GESTOPAGO_RECHAZADO.estadoCompra());
        assertEquals(EstadoCompra.RECHAZADA, CodigoErrorPago.SALDO_INSUFICIENTE.estadoCompra());
        assertEquals(EstadoCompra.RECHAZADA, CodigoErrorPago.PIN_INCORRECTO.estadoCompra());
    }

    @Test
    void erroresDeConciliacionNoSonEstadoDeCompra() {
        assertNull(CodigoErrorPago.TRANSACCION_NO_ENCONTRADA.estadoCompra());
        assertNull(CodigoErrorPago.TRANSACCION_NO_CONCILIABLE.estadoCompra());
    }

    @Test
    void estatusDeLaTransaccionSeTraduceAlEstadoDeCompra() {
        assertEquals(EstadoCompra.EXITOSA, EstadoCompra.de(EstatusTransaccion.EXITOSA));
        assertEquals(EstadoCompra.RECHAZADA, EstadoCompra.de(EstatusTransaccion.FALLIDA));
        assertEquals(EstadoCompra.PENDIENTE, EstadoCompra.de(EstatusTransaccion.PENDIENTE));
        assertEquals(EstadoCompra.PENDIENTE, EstadoCompra.de(EstatusTransaccion.EN_REVISION));
    }
}
