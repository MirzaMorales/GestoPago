package com.proyecto.servicios.exception;

/** No fue posible enviar el código por correo (SMTP no configurado o caído). */
public class CorreoNoDisponibleException extends RuntimeException {
    public CorreoNoDisponibleException() {
        super("No fue posible enviar el código de verificación. Intente más tarde");
    }
}
