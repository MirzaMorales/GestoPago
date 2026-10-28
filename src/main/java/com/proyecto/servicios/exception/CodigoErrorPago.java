package com.proyecto.servicios.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Catálogo de errores del pago de servicios. Cada error tiene un código propio de la API,
 * el estatus HTTP con el que se responde y un mensaje estándar (sin datos sensibles).
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
    TIPO_FRONT_NO_COINCIDE(1008, HttpStatus.UNPROCESSABLE_ENTITY, "El tipoFront no corresponde al producto indicado"),

    // Idempotencia, duplicados y reglas de monto
    IDEMPOTENCY_KEY_REUTILIZADA(1009, HttpStatus.UNPROCESSABLE_ENTITY, "La llave de idempotencia ya se usó con una solicitud distinta"),
    PAGO_EN_PROCESO(1010, HttpStatus.CONFLICT, "Ya existe un pago en proceso o en revisión para esta operación"),
    PAGO_PREVIO_FALLIDO(1011, HttpStatus.CONFLICT, "El pago anterior con esta llave no se completó; use una llave nueva para reintentar"),
    PIN_BLOQUEADO(1012, HttpStatus.LOCKED, "Demasiados intentos fallidos; el PIN está bloqueado temporalmente"),
    MONTO_NO_COINCIDE(1013, HttpStatus.UNPROCESSABLE_ENTITY, "El monto no corresponde al precio del producto"),
    MONTO_EXCEDE_LIMITE(1014, HttpStatus.UNPROCESSABLE_ENTITY, "El monto excede el límite permitido por operación"),
    TIPO_FRONT_NO_SOPORTADO(1015, HttpStatus.UNPROCESSABLE_ENTITY, "Este tipo de producto no se puede pagar desde esta API"),
    REFERENCIA_INVALIDA(1016, HttpStatus.UNPROCESSABLE_ENTITY, "El teléfono o la referencia no cumple el formato que exige el producto"),
    PRECIO_NO_DISPONIBLE(1017, HttpStatus.SERVICE_UNAVAILABLE, "El catálogo no tiene el precio del producto; actualice el catálogo"),
    IDEMPOTENCY_KEY_INVALIDA(1018, HttpStatus.BAD_REQUEST, "El header Idempotency-Key es obligatorio (8 a 64 caracteres: letras, números, guion o guion bajo)"),

    // Errores del consumo de GestoPago (sendTx / confirmTx)
    GESTOPAGO_RECHAZADO(1101, HttpStatus.UNPROCESSABLE_ENTITY, "GestoPago rechazó la transacción"),
    GESTOPAGO_NO_DISPONIBLE(1102, HttpStatus.BAD_GATEWAY, "No fue posible comunicarse con GestoPago"),
    GESTOPAGO_RESPUESTA_INVALIDA(1103, HttpStatus.BAD_GATEWAY, "La respuesta de GestoPago no pudo interpretarse"),
    GESTOPAGO_TOKEN_NO_DISPONIBLE(1104, HttpStatus.SERVICE_UNAVAILABLE, "No hay un token activo de GestoPago"),
    FIRMA_NO_CONFIGURADA(1105, HttpStatus.INTERNAL_SERVER_ERROR, "La firma de GestoPago no está configurada o es inválida"),
    GESTOPAGO_DUPLICADO(1106, HttpStatus.CONFLICT, "GestoPago indica que esta operación ya fue aplicada anteriormente"),
    GESTOPAGO_TIMEOUT(1107, HttpStatus.GATEWAY_TIMEOUT, "GestoPago no respondió a tiempo; la operación quedó en revisión"),

    // Conciliación
    TRANSACCION_NO_ENCONTRADA(1201, HttpStatus.NOT_FOUND, "No se encontró la transacción de pago"),
    TRANSACCION_NO_CONCILIABLE(1202, HttpStatus.CONFLICT, "La transacción no está pendiente de conciliación");

    private final int codigo;
    private final HttpStatus status;
    private final String mensaje;

    CodigoErrorPago(int codigo, HttpStatus status, String mensaje) {
        this.codigo = codigo;
        this.status = status;
        this.mensaje = mensaje;
    }
}
