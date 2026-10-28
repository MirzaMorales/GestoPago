package com.proyecto.servicios.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagoServicioResponse {

    private Long idTransaccion;
    /** true si es la repetición de una solicitud ya completada (misma Idempotency-Key): no se cobró de nuevo. */
    private boolean repetida;
    private String numeroCuenta;
    private BigDecimal monto;
    /** Saldo de la cuenta después de descontar el pago. */
    private BigDecimal saldoRestante;
    private Integer idServicio;
    private Integer idProducto;
    private String producto;
    private String referencia;
    private BigDecimal comision;
    private String idTxGestoPago;
    private String numAutorizacion;
    private String fecha;
    /** PIN de la tarjeta de regalo (solo en productos que lo devuelven). */
    private String pinProducto;
    private String leyenda;
}
