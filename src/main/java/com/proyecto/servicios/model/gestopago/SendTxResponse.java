package com.proyecto.servicios.model.gestopago;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Respuesta XML de sendTx. CODIGO "01" es éxito; cualquier otro valor es error. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendTxResponse {

    private Integer codigo;
    private String idTx;
    private String numAutorizacion;
    private BigDecimal saldo;
    private BigDecimal comision;
    private String fecha;
    private BigDecimal monto;
    private String referencia;
    private String pin;
    /** Texto descriptivo devuelto por GestoPago (legend / TEXTO). */
    private String leyenda;

    public boolean isExitoso() {
        return Integer.valueOf(1).equals(codigo);
    }
}
