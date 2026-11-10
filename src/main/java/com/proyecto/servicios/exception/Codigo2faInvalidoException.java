package com.proyecto.servicios.exception;

/** Código de verificación (2FA) incorrecto, expirado o ya usado. */
public class Codigo2faInvalidoException extends RuntimeException {
    public Codigo2faInvalidoException(String message) {
        super(message);
    }
}
