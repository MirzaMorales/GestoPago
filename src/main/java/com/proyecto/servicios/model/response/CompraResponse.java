package com.proyecto.servicios.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Compra exitosa del historial (la lista y el detalle usan la misma estructura). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompraResponse {

    /** Referencia de la transacción en este sistema. */
    private Long idTransaccion;
    /** Fecha y hora en que GestoPago confirmó la compra. */
    private OffsetDateTime fecha;
    private Integer idProducto;
    private Integer idServicio;
    private String producto;
    /** Teléfono, número de contrato o referencia que se pagó. */
    private String referencia;
    private BigDecimal monto;
    private BigDecimal comision;
    /** Identificador de la transacción en GestoPago. */
    private String idTxGestoPago;
    private String numAutorizacion;
    /** Cuenta con la que se pagó, enmascarada (solo los últimos 4 dígitos). */
    private String cuenta;
    private String estatus;
}
