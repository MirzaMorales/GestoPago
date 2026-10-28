package com.proyecto.servicios.exception;

import lombok.Getter;

@Getter
public class PagoException extends RuntimeException {

    private final CodigoErrorPago error;
    /** Información adicional opcional (por ejemplo el texto devuelto por GestoPago). */
    private final String detalle;

    public PagoException(CodigoErrorPago error) {
        this(error, null);
    }

    public PagoException(CodigoErrorPago error, String detalle) {
        super(error.getMensaje());
        this.error = error;
        this.detalle = detalle;
    }
}
