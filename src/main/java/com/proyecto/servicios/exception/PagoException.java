package com.proyecto.servicios.exception;

import lombok.Getter;

@Getter
public class PagoException extends RuntimeException {

    private final CodigoErrorPago error;
    /** Información adicional opcional (por ejemplo el texto devuelto por GestoPago). */
    private final String detalle;
    /** Transacción afectada, cuando ya existe y su resultado puede cambiar (para consultarla con POST /pagos/estado). */
    private final Long idTransaccion;

    public PagoException(CodigoErrorPago error) {
        this(error, null);
    }

    public PagoException(CodigoErrorPago error, String detalle) {
        this(error, detalle, null);
    }

    /** Código de respuesta de GestoPago (por ejemplo "55"), cuando el rechazo viene del proveedor. */
    private final String codigoProveedor;
    /** Mensaje de GestoPago ya saneado (sin etiquetas ni caracteres de control, longitud acotada). */
    private final String mensajeProveedor;

    public PagoException(CodigoErrorPago error, String detalle, Long idTransaccion) {
        this(error, detalle, idTransaccion, null, null);
    }

    public PagoException(CodigoErrorPago error, String detalle, Long idTransaccion,
                         String codigoProveedor, String mensajeProveedor) {
        super(error.getMensaje());
        this.error = error;
        this.detalle = detalle;
        this.idTransaccion = idTransaccion;
        this.codigoProveedor = codigoProveedor;
        this.mensajeProveedor = mensajeProveedor;
    }
}
