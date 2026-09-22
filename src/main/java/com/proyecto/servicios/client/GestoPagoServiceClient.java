package com.proyecto.servicios.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "gestoPagoService", url = "${gestopago.service.url}")
public interface GestoPagoServiceClient {

    @GetMapping(value = "/sistema/service/getProductList.do")
    String getProductListRaw(@RequestHeader("Authorization") String bearerToken);
}
