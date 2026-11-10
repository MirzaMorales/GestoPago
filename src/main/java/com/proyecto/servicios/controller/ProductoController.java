package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.gestopago.ProductoListResponse;
import com.proyecto.servicios.service.ProductoService;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class ProductoController {

    @Autowired
    private ProductoService productoService;

    /** Consulta en vivo al proveedor (limitada a pocas veces al día): solo ADMIN. Las apps usan /productos/almacenados. */
    @GetMapping(value = "/productos", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductoListResponse> obtenerProductos() {
        return respuesta(productoService.obtenerListaProductos());
    }

    /**
     * Igual que /productos. El header Authorization de esta API lleva el JWT propio, nunca el del proveedor;
     * para diagnóstico se puede forzar un token de GestoPago con el header X-GestoPago-Token.
     */
    @GetMapping(value = "/getProductList", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductoListResponse> getProductList(
            @RequestHeader(value = "X-GestoPago-Token", required = false)
            @Size(max = 4096, message = "El encabezado X-GestoPago-Token excede el tamaño permitido") String tokenProveedor) {
        if (tokenProveedor != null && !tokenProveedor.isBlank()) {
            return respuesta(productoService.obtenerListaProductos(tokenProveedor));
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
