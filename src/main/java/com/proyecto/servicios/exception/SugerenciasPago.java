package com.proyecto.servicios.exception;

/**
 * Qué puede hacer el cliente ante cada resultado no exitoso de una compra. Se calcula en el servidor para que
 * la web y la app móvil muestren exactamente lo mismo.
 */
public final class SugerenciasPago {

    private SugerenciasPago() {
    }

    public static String para(CodigoErrorPago error, String codigoProveedor) {
        if (error == CodigoErrorPago.GESTOPAGO_RECHAZADO) {
            return porCodigoProveedor(codigoProveedor);
        }
        return switch (error) {
            case PIN_INCORRECTO -> "Revisa tu PIN de 4 dígitos e inténtalo de nuevo. Tras varios intentos fallidos se bloquea temporalmente.";
            case PIN_BLOQUEADO -> "Espera unos minutos antes de volver a intentarlo o cambia tu PIN desde Mi cuenta.";
            case PIN_NO_CONFIGURADO -> "Configura tu PIN de compras en Mi cuenta antes de comprar.";
            case SALDO_INSUFICIENTE -> "Tu cuenta no tiene saldo suficiente. Solicita un abono e inténtalo de nuevo.";
            case CUENTA_NO_ACTIVA, CUENTA_NO_ENCONTRADA -> "Usa una cuenta activa o comunícate con soporte.";
            case REFERENCIA_INVALIDA -> "Revisa el teléfono o la referencia: debe tener el formato que indica el producto.";
            case MONTO_NO_COINCIDE, PRECIO_NO_DISPONIBLE -> "El precio del producto cambió. Actualiza el catálogo e inténtalo de nuevo.";
            case MONTO_EXCEDE_LIMITE -> "El importe supera el máximo permitido por operación. Divide el pago o usa un importe menor.";
            case GESTOPAGO_DUPLICADO -> "Esta operación ya se había aplicado. Revisa tu historial antes de volver a pagar.";
            case GESTOPAGO_TIMEOUT, PAGO_EN_PROCESO ->
                    "No vuelvas a pagar: la compra se está confirmando con GestoPago. Consulta su estado en unos minutos.";
            case GESTOPAGO_NO_DISPONIBLE, GESTOPAGO_RESPUESTA_INVALIDA, GESTOPAGO_TOKEN_NO_DISPONIBLE, FIRMA_NO_CONFIGURADA ->
                    "Hubo un problema de comunicación con GestoPago. Revisa tu historial y vuelve a intentarlo más tarde.";
            default -> "Revisa los datos de la compra e inténtalo de nuevo. Si el problema continúa, comunícate con soporte.";
        };
    }

    /** Códigos documentados por GestoPago en sus ejemplos; el resto se explica con el mensaje del propio proveedor. */
    private static String porCodigoProveedor(String codigo) {
        String c = codigo == null ? "" : codigo.replaceFirst("^0+(?=\\d)", "");
        return switch (c) {
            case "2" -> "GestoPago no pudo procesar la operación con esos datos o sin saldo disponible del comercio. "
                    + "Revisa la referencia o inténtalo más tarde.";
            case "6" -> "Esta operación ya se había aplicado. No es posible pagar la misma factura dos veces.";
            case "70" -> "GestoPago no aplicó la operación. Puedes intentarlo de nuevo.";
            case "100", "101" -> "Los códigos promocionales no son válidos o no cubren el importe.";
            default -> "GestoPago no aceptó la operación. Verifica la referencia y el importe de tu recibo e inténtalo de nuevo; "
                    + "si persiste, comunícate con soporte indicando el folio.";
        };
    }

    /** Quita etiquetas, caracteres de control y espacios repetidos del texto del proveedor y limita su longitud. */
    public static String sanear(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto
                .replaceAll("<[^>]*>", " ")
                .replaceAll("[\\p{Cntrl}<>{}\\uFFFD]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (limpio.isEmpty()) {
            return null;
        }
        return limpio.length() > 200 ? limpio.substring(0, 200).trim() + "…" : limpio;
    }
}
