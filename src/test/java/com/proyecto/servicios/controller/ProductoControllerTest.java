package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.gestopago.ProductoListResponse;
import com.proyecto.servicios.service.ProductoService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductoControllerTest {

    private final ProductoService productoService = mock(ProductoService.class);
    private final ProductoController controller = new ProductoController();

    @Test
    void mapsUpstreamFailureToBadGateway() {
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "productoService", productoService);
        when(productoService.obtenerListaProductos()).thenReturn(ProductoListResponse.builder()
                .codigo(1)
                .mensaje("No se pudo obtener o procesar la respuesta del servicio de productos")
                .build());

        var response = controller.obtenerProductos();

        assertEquals(502, response.getStatusCode().value());
        assertEquals(1, response.getBody().getCodigo());
        assertEquals("No se pudo obtener o procesar la respuesta del servicio de productos",
                response.getBody().getMensaje());
    }

    @Test
    void mapsUnavailableTokenToServiceUnavailable() {
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "productoService", productoService);
        when(productoService.obtenerListaProductos()).thenReturn(ProductoListResponse.builder()
                .codigo(2)
                .mensaje("No se pudo obtener un token activo de GestoPago")
                .build());

        var response = controller.obtenerProductos();

        assertEquals(503, response.getStatusCode().value());
    }
}
