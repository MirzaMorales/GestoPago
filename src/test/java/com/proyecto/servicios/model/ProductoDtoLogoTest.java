package com.proyecto.servicios.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.model.gestopago.ProductoDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductoDtoLogoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void logoSeArmaConElIdDelServicio() {
        ProductoDTO dto = ProductoDTO.builder().idProducto(209).idServicio(79).build();
        assertEquals("https://gestopago.portalventas.net/sistema/images/gestopago/servicios/79.png", dto.getLogoUrl());
    }

    @Test
    void sinIdServicioNoHayLogo() {
        assertNull(ProductoDTO.builder().idProducto(1).build().getLogoUrl());
    }

    @Test
    void elLogoSaleEnElJsonYSeIgnoraAlLeerDesdeCache() throws Exception {
        String json = mapper.writeValueAsString(ProductoDTO.builder().idProducto(209).idServicio(79).build());
        assertTrue(json.contains("\"logoUrl\":\"https://gestopago.portalventas.net/sistema/images/gestopago/servicios/79.png\""));

        ProductoDTO leido = mapper.readValue(json, ProductoDTO.class);
        assertEquals(79, leido.getIdServicio());
    }

    @Test
    void productoConPrecioFijoOImporteLibreEstaDisponibleYUnoSinPrecioNo() {
        ProductoDTO fijo = ProductoDTO.builder().idProducto(1).tipoFront(1).precio(new java.math.BigDecimal("150")).build();
        ProductoDTO libre = ProductoDTO.builder().idProducto(2).tipoFront(2).build();
        ProductoDTO sinPrecio = ProductoDTO.builder().idProducto(3).tipoFront(1).build();
        ProductoDTO otro = ProductoDTO.builder().idProducto(4).tipoFront(9).build();

        assertTrue(fijo.isDisponibleParaCompra());
        assertFalse(fijo.isMontoLibre());
        assertEquals("Recargas y tarjetas", fijo.getCategoria());
        assertTrue(libre.isDisponibleParaCompra());
        assertTrue(libre.isMontoLibre());
        assertEquals("Pago de servicios", libre.getCategoria());
        assertFalse(sinPrecio.isDisponibleParaCompra());
        assertFalse(otro.isDisponibleParaCompra());
        assertEquals("Otros", otro.getCategoria());
    }
}
