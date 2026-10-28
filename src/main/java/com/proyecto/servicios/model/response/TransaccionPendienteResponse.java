package com.proyecto.servicios.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransaccionPendienteResponse {

    private Long id;
    private String numeroCuenta;
    private Integer idProducto;
    private Integer idServicio;
    private BigDecimal monto;
    private String estatus;
    private String upc;
    private OffsetDateTime fechaEnvio;
    private String detalle;
    /** true cuando ya pasaron las 24 h en que confirmTx está disponible: debe resolverse manualmente. */
    private boolean requiereResolucionManual;
}
