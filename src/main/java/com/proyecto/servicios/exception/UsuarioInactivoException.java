package com.proyecto.servicios.exception;

public class UsuarioInactivoException extends RuntimeException {
    public UsuarioInactivoException(String message) {
        super(message);
    }
}
