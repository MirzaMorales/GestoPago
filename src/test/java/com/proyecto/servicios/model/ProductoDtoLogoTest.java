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
}
