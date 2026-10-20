package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.model.gestopago.SendTxResponse;
import com.proyecto.servicios.model.request.PagoServicioRequest;

public interface GestoPagoTxService {

    /**
     * Arma el payload de sendTx, lo firma (AES), obtiene el token y llama a GestoPago.
     * Devuelve la respuesta solo si CODIGO = 01; en otro caso lanza PagoException.
     */
    SendTxResponse enviarTx(PagoServicioRequest request, ProductoEntity producto);
}
