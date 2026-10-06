package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.gestopago.ProductoListResponse;
import com.proyecto.servicios.service.ProductoService;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class ProductoController {

    @Autowired
    private ProductoService productoService;

    @GetMapping(value = "/productos", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductoListResponse> obtenerProductos() {
        return respuesta(productoService.obtenerListaProductos());
    }

    @GetMapping(value = "/getProductList", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductoListResponse> getProductList(
            @RequestHeader(value = "Authorization", required = false)
            @Size(max = 4096, message = "El encabezado Authorization excede el tamaño permitido") String token) {
        if (token != null && !token.isBlank()) {
            return respuesta(productoService.obtenerListaProductos(token));
        }
        return respuesta(productoService.obtenerListaProductos());
    }

    @GetMapping(value = "/productos/almacenados", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductoListResponse> obtenerProductosAlmacenados() {
        return respuesta(productoService.obtenerProductosAlmacenados());
    }

    private ResponseEntity<ProductoListResponse> respuesta(ProductoListResponse response) {
        if (response.getCodigo() != null && response.getCodigo() == 0) {
            return ResponseEntity.ok(response);
        }
        HttpStatus status = response.getCodigo() == 2
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(response);
    }
}
