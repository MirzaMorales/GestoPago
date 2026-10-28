package com.proyecto.servicios.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.exception.CodigoErrorPago;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.request.PagoServicioRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Arma el JSON de sendTx según las reglas de GestoPago:
 * <ul>
 *   <li>Productos canjeables (idCatTipoServicio 10 u 11): telefono fijo "#1111111111", sin referencia ni monto.</li>
 *   <li>tipoFront 1 (recargas): el teléfono va en "telefono"; el precio lo fija el producto.</li>
 *   <li>tipoFront 2 (pago de servicios): telefono fijo "1111111111"; la referencia va en "referencia" y el importe en "montoPago".</li>
 * </ul>
 * El JSON se construye UNA vez, se guarda y se reutiliza tal cual en confirmTx.
 */
@Component
public class SendTxPayloadBuilder {

    private static final DateTimeFormatter HORA_LOCAL = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String TELEFONO_FIJO_SERVICIOS = "1111111111";
    private static final String TELEFONO_FIJO_CANJEABLES = "#1111111111";
    private static final String BASE36 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${gestopago.tx.unidad:083}")
    private String unidad;

    @Value("${gestopago.tx.zona-horaria:America/Mexico_City}")
    private String zonaHoraria;

    public static boolean esCanjeable(ProductoEntity producto) {
        Integer catTipo = producto.getIdCatTipoServicio();
        return catTipo != null && (catTipo == 10 || catTipo == 11);
    }

    public String construir(PagoServicioRequest request, ProductoEntity producto, String upc) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("idProducto", producto.getIdProducto());
        payload.put("idServicio", producto.getIdServicio());

        if (esCanjeable(producto)) {
            payload.put("telefono", TELEFONO_FIJO_CANJEABLES);
        } else if (Integer.valueOf(2).equals(producto.getTipoFront())) {
            payload.put("telefono", TELEFONO_FIJO_SERVICIOS);
            payload.put("referencia", request.getTelefonoReferencia());
            payload.put("montoPago", request.getMonto());
        } else {
            payload.put("telefono", request.getTelefonoReferencia());
        }

        payload.put("horaLocal", ZonedDateTime.now(ZoneId.of(zonaHoraria)).format(HORA_LOCAL));
        payload.put("upc", upc);
        payload.put("unidad", unidad);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new PagoException(CodigoErrorPago.GESTOPAGO_RESPUESTA_INVALIDA);
        }
    }

    /** Identificador único de operación: 14 caracteres alfanuméricos (timestamp base36 + aleatorio); el proveedor admite 3 a 15. */
    public String generarUpc() {
        StringBuilder upc = new StringBuilder(Long.toString(System.currentTimeMillis(), 36).toUpperCase());
        while (upc.length() < 14) {
            upc.append(BASE36.charAt(RANDOM.nextInt(BASE36.length())));
        }
        return upc.substring(0, 14);
    }
}
