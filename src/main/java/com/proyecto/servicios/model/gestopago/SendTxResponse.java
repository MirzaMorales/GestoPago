package com.proyecto.servicios.model.gestopago;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Respuesta XML de sendTx / confirmTx.
 * sendTx: CODIGO 01 = aplicada, 06 = ya aplicada antes (duplicado), 82 = tiempo de espera (resultado desconocido).
 * confirmTx: CODIGO 06 = aplicada, 70 = no aplicada.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendTxResponse {

    public static final int CODIGO_EXITO = 1;
    public static final int CODIGO_DUPLICADO_O_APLICADA = 6;
    public static final int CODIGO_NO_APLICADA = 70;
    public static final int CODIGO_TIMEOUT = 82;

    private Integer codigo;
    private String idTx;
    private String numAutorizacion;
    private BigDecimal saldo;
    private BigDecimal comision;
    private String fecha;
    private BigDecimal monto;
    private String referencia;
    /** PIN o folio de productos canjeables; se devuelve al cliente pero nunca se guarda ni se registra. */
    private String pin;
    /** Texto descriptivo devuelto por GestoPago (legend / TEXTO). */
    private String leyenda;
    /** XML original con el PIN enmascarado, listo para la bitácora de intercambios. */
    private String respuestaCruda;

    public boolean isExitoso() {
        return Integer.valueOf(CODIGO_EXITO).equals(codigo);
    }

    public boolean esTimeout() {
        return Integer.valueOf(CODIGO_TIMEOUT).equals(codigo);
    }
}
