package com.proyecto.servicios.service;

import com.proyecto.servicios.model.request.PagoServicioRequest;
import com.proyecto.servicios.model.response.PagoServicioResponse;

public interface PagoService {

    /**
     * Valida, retiene el saldo, envía el pago a GestoPago (sendTx) y confirma, devuelve el saldo o deja la
     * operación en revisión según el resultado. La {@code idempotencyKey} evita cargos duplicados: repetir
     * la misma solicitud con la misma llave no vuelve a cobrar.
     * Lanza PagoException con el código de error correspondiente si algo falla.
     */
    PagoServicioResponse pagarServicio(PagoServicioRequest request, String idempotencyKey);
}
