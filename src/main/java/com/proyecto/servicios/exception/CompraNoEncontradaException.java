package com.proyecto.servicios.exception;

/** La compra no existe, no fue exitosa o no pertenece al usuario (no se distingue para no filtrar información). */
public class CompraNoEncontradaException extends RuntimeException {
    public CompraNoEncontradaException(Long id) {
        super("No se encontró la compra con identificador " + id);
    }
}
