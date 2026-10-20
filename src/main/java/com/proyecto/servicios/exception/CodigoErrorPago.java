package com.proyecto.servicios.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Catálogo de errores del pago de servicios. Cada error tiene un código propio de la API,
 * el estatus HTTP con el que se responde y un mensaje estándar.
 */
@Getter
public enum CodigoErrorPago {

    CUENTA_NO_ENCONTRADA(1001, HttpStatus.NOT_FOUND, "No se encontró la cuenta bancaria"),
    CUENTA_NO_ACTIVA(1002, HttpStatus.UNPROCESSABLE_ENTITY, "La cuenta no está activa"),
    PIN_NO_CONFIGURADO(1003, HttpStatus.UNPROCESSABLE_ENTITY, "La cuenta no tiene un PIN de autorización configurado"),
    PIN_INCORRECTO(1004, HttpStatus.FORBIDDEN, "El PIN de autorización es incorrecto"),
    SALDO_INSUFICIENTE(1005, HttpStatus.UNPROCESSABLE_ENTITY, "El saldo de la cuenta es insuficiente para realizar el pago"),
    PRODUCTO_NO_ENCONTRADO(1006, HttpStatus.NOT_FOUND, "El producto indicado no existe en el catálogo de GestoPago"),
    SERVICIO_NO_COINCIDE(1007, HttpStatus.UNPROCESSABLE_ENTITY, "El idServicio no corresponde al idProducto indicado"),
    TIPO_FRONT_NO_COINCIDE(1008, HttpStatus.UNPROCESSABLE_ENTITY, "El tipoFront no corresponde al producto indicado");

    private final int codigo;
    private final HttpStatus status;
    private final String mensaje;

    CodigoErrorPago(int codigo, HttpStatus status, String mensaje) {
        this.codigo = codigo;
        this.status = status;
        this.mensaje = mensaje;
    }
}
