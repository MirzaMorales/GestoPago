package com.proyecto.servicios.exception;

/** Se superó el límite de intentos o de envíos de códigos 2FA. */
public class DemasiadosIntentosException extends RuntimeException {
    public DemasiadosIntentosException(String message) {
        super(message);
    }
}
