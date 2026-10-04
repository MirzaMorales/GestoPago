package com.proyecto.servicios.exception;

public class ClienteYaRegistradoException extends RuntimeException {
    public ClienteYaRegistradoException(String message) {
        super(message);
    }
}
