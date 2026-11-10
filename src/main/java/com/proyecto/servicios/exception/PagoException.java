package com.proyecto.servicios.exception;

import lombok.Getter;

@Getter
public class PagoException extends RuntimeException {

    private final CodigoErrorPago error;
    /** Información adicional opcional (por ejemplo el texto devuelto por GestoPago). */
    private final String detalle;
    /** Transacción afectada, cuando ya existe y su resultado puede cambiar (para consultarla con POST /pagos/estado). */
    private final Long idTransaccion;

    public PagoException(CodigoErrorPago error) {
        this(error, null);
    }

    public PagoException(CodigoErrorPago error, String detalle) {
        this(error, detalle, null);
    }

    public PagoException(CodigoErrorPago error, String detalle, Long idTransaccion) {
        super(error.getMensaje());
        this.error = error;
        this.detalle = detalle;
        this.idTransaccion = idTransaccion;
    }
}
