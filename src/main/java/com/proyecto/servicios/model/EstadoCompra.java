package com.proyecto.servicios.model;

import com.proyecto.servicios.entity.EstatusTransaccion;

/**
 * Resultado de una compra tal como lo debe mostrar el cliente (web o móvil).
 * Solo EXITOSA cuenta como compra realizada.
 */
public enum EstadoCompra {
    /** GestoPago confirmó la compra. */
    EXITOSA,
    /** La compra no se realizó (validación, saldo, PIN o rechazo de GestoPago); el saldo no se descontó o fue devuelto. */
    RECHAZADA,
    /** Aún no se sabe el resultado: el monto está retenido y se confirmará con GestoPago. Consultar de nuevo más tarde. */
    PENDIENTE,
    /** No fue posible comunicarse con GestoPago o hubo un problema técnico; ver el detalle para saber si el monto quedó retenido. */
    ERROR_COMUNICACION;

    public static EstadoCompra de(EstatusTransaccion estatus) {
        return switch (estatus) {
            case EXITOSA -> EXITOSA;
            case FALLIDA -> RECHAZADA;
            case PENDIENTE, EN_REVISION -> PENDIENTE;
        };
    }
}
