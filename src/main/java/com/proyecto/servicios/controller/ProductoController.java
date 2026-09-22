package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.gestopago.ProductoListResponse;
import com.proyecto.servicios.service.ProductoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductoController {

    @Autowired
    private ProductoService productoService;

    @GetMapping(value = "/productos", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductoListResponse> obtenerProductos() {
        return new ResponseEntity<>(productoService.obtenerListaProductos(), HttpStatus.OK);
    }

    @GetMapping(value = "/getProductList", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductoListResponse> getProductList(
            @RequestHeader(value = "Authorization", required = false) String token) {
        if (token != null && !token.isBlank()) {
            return new ResponseEntity<>(productoService.obtenerListaProductos(token), HttpStatus.OK);
        }
        return new ResponseEntity<>(productoService.obtenerListaProductos(), HttpStatus.OK);
    }

    @GetMapping(value = "/productos/almacenados", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductoListResponse> obtenerProductosAlmacenados() {
        return new ResponseEntity<>(productoService.obtenerProductosAlmacenados(), HttpStatus.OK);
    }
}

