package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.exception.CodigoErrorPago;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.request.PagoServicioRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reglas de monto y referencia según el tipo de producto del catálogo de GestoPago.
 * El monto nunca se confía al cliente cuando el proveedor ya define el precio.
 * <ul>
 *   <li>tipoFront 1 (recargas / tarjetas): se descuenta el precio del catálogo; el monto enviado debe coincidir.</li>
 *   <li>tipoFront 2 (pago de servicios): el importe lo define el cliente, hasta el límite por operación.</li>
 *   <li>Otros tipos (verificación, cash-in/out, transferencias): no soportados.</li>
 * </ul>
 */
@Component
public class ReglasMontoPago {

    @Value("${pagos.monto-maximo:20000.00}")
    private BigDecimal montoMaximo;

    /** Devuelve el monto a descontar de la cuenta o lanza PagoException si la solicitud no es válida para el producto. */
    public BigDecimal montoADebitar(PagoServicioRequest request, ProductoEntity producto) {
        Integer tipoFront = producto.getTipoFront();
        if (!Integer.valueOf(1).equals(tipoFront) && !Integer.valueOf(2).equals(tipoFront)) {
            throw new PagoException(CodigoErrorPago.TIPO_FRONT_NO_SOPORTADO);
        }

        BigDecimal solicitado = request.getMonto();

        if (Integer.valueOf(1).equals(tipoFront)) {
            BigDecimal precio = producto.getPrecio();
            if (precio == null || precio.signum() <= 0) {
                throw new PagoException(CodigoErrorPago.PRECIO_NO_DISPONIBLE);
            }
            if (solicitado.compareTo(precio) != 0) {
                throw new PagoException(CodigoErrorPago.MONTO_NO_COINCIDE);
            }
            if (!SendTxPayloadBuilder.esCanjeable(producto) && !request.getTelefonoReferencia().matches("^\\d{10}$")) {
                throw new PagoException(CodigoErrorPago.REFERENCIA_INVALIDA);
            }
            return precio.setScale(2, RoundingMode.HALF_UP);
        }

        if (solicitado.compareTo(montoMaximo) > 0) {
            throw new PagoException(CodigoErrorPago.MONTO_EXCEDE_LIMITE);
        }
        return solicitado.setScale(2, RoundingMode.HALF_UP);
    }
}
