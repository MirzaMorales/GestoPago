package com.proyecto.servicios.service;

import com.proyecto.servicios.model.request.PagoServicioRequest;
import com.proyecto.servicios.model.response.PagoValidadoResponse;

public interface PagoService {

    /**
     * Valida cuenta activa, PIN, producto y saldo. Lanza PagoException con el
     * código de error correspondiente si alguna validación falla.
     */
    PagoValidadoResponse validarPago(PagoServicioRequest request);
}
