package com.proyecto.servicios.entity;

public enum EstatusTransaccion {
    /** Saldo retenido; aún no se conoce el resultado de GestoPago. */
    PENDIENTE,
    EXITOSA,
    /** GestoPago rechazó o no recibió la petición; el saldo fue devuelto. */
    FALLIDA,
    /** No se sabe si GestoPago la procesó; el saldo sigue retenido hasta conciliar. */
    EN_REVISION
}
