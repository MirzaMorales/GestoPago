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

    @Test
    void getProductListNoTomaElJwtPropioComoTokenDelProveedor() {
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "productoService", productoService);
        when(productoService.obtenerListaProductos()).thenReturn(ProductoListResponse.builder().codigo(0).build());

        // sin X-GestoPago-Token se usa el token guardado del proveedor, aunque el cliente mande su JWT en Authorization
        var response = controller.getProductList(null);

        assertEquals(200, response.getStatusCode().value());
        org.mockito.Mockito.verify(productoService).obtenerListaProductos();
        org.mockito.Mockito.verify(productoService, org.mockito.Mockito.never()).obtenerListaProductos(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getProductListUsaElTokenDelProveedorSoloSiSeEnviaEnSuHeader() {
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "productoService", productoService);
        when(productoService.obtenerListaProductos("tok")).thenReturn(ProductoListResponse.builder().codigo(0).build());

        controller.getProductList("tok");

        org.mockito.Mockito.verify(productoService).obtenerListaProductos("tok");
    }
}
