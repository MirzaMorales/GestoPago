package com.proyecto.servicios.exception;

public class ContrasenaInvalidaException extends RuntimeException {
    public ContrasenaInvalidaException(String message) {
        super(message);
    }
}
