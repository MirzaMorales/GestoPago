package com.proyecto.servicios.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "gestoPagoService", url = "${gestopago.service.url}")
public interface GestoPagoServiceClient {

    @GetMapping(value = "/sistema/service/getProductList.do")
    String getProductListRaw(@RequestHeader("Authorization") String bearerToken);

    /** Cuerpo x-www-form-urlencoded con los campos "random" y "signed" (payload JSON cifrado). */
    @PostMapping(value = "/sistema/service/sendTx.do", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    String sendTxRaw(@RequestHeader("Authorization") String bearerToken,
                     @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                     @RequestBody MultiValueMap<String, String> form);

    /**
     * Consulta si una transacción fue aplicada. Debe enviarse con exactamente el mismo payload que sendTx,
     * no antes de 62 s del envío original y dentro de las 24 h posteriores.
     */
    @PostMapping(value = "/sistema/service/confirmTx.do", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    String confirmTxRaw(@RequestHeader("Authorization") String bearerToken,
                        @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                        @RequestBody MultiValueMap<String, String> form);
}
