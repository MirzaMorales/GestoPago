package com.proyecto.servicios.exception;

import lombok.Getter;

@Getter
public class PagoException extends RuntimeException {

    private final CodigoErrorPago error;

    public PagoException(CodigoErrorPago error) {
        super(error.getMensaje());
        this.error = error;
    }
}
