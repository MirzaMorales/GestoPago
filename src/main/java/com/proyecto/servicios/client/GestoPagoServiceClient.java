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
                     @RequestBody MultiValueMap<String, String> form);
}
