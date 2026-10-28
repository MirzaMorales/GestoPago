package com.proyecto.servicios.service;

import com.proyecto.servicios.model.gestopago.SendTxResponse;

public interface GestoPagoTxService {

    /**
     * Firma (AES) el payload, obtiene el token y llama a sendTx. Devuelve la respuesta del proveedor
     * sea cual sea su CODIGO; solo lanza PagoException ante fallos técnicos (token, firma, red, XML inválido).
     */
    SendTxResponse enviarTx(String payloadJson);

    /**
     * Consulta con confirmTx si una transacción fue aplicada. El payload debe ser exactamente el mismo
     * que se envió a sendTx (misma horaLocal y mismo UPC).
     */
    SendTxResponse confirmarTx(String payloadJson);
}
