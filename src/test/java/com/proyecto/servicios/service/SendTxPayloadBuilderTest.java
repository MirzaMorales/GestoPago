package com.proyecto.servicios.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.model.request.PagoServicioRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class SendTxPayloadBuilderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private SendTxPayloadBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SendTxPayloadBuilder();
        ReflectionTestUtils.setField(builder, "objectMapper", mapper);
        ReflectionTestUtils.setField(builder, "unidad", "083");
        ReflectionTestUtils.setField(builder, "zonaHoraria", "America/Mexico_City");
    }

    private PagoServicioRequest request(String telefonoReferencia) {
        return PagoServicioRequest.builder()
                .numeroCuenta("1234567890123456").pin("1234")
                .monto(new BigDecimal("100.00")).telefonoReferencia(telefonoReferencia)
                .idServicio(133).idProducto(582).tipoFront(1).build();
    }

    private ProductoEntity producto(Integer tipoFront, Integer catTipo) {
        return ProductoEntity.builder().idProducto(582).idServicio(133).tipoFront(tipoFront).idCatTipoServicio(catTipo).build();
    }

    private JsonNode payload(PagoServicioRequest request, ProductoEntity producto) throws Exception {
        return mapper.readTree(builder.construir(request, producto, "UPC123"));
    }

    @Test
    void recarga_tipoFront1_enviaTelefonoDelRequestSinMontoNiReferencia() throws Exception {
        JsonNode json = payload(request("5555555551"), producto(1, 2));

        assertEquals("5555555551", json.get("telefono").asText());
        assertEquals(582, json.get("idProducto").asInt());
        assertEquals(133, json.get("idServicio").asInt());
        assertEquals("083", json.get("unidad").asText());
        assertEquals("UPC123", json.get("upc").asText());
        assertFalse(json.has("referencia"));
        assertFalse(json.has("montoPago"));
        assertTrue(json.get("horaLocal").asText().matches("\\d{14}"));
    }

    @Test
    void pagoDeServicio_tipoFront2_telefonoFijoReferenciaYMontoPago() throws Exception {
        JsonNode json = payload(request("ABC123"), producto(2, 5));

        assertEquals("1111111111", json.get("telefono").asText());
        assertEquals("ABC123", json.get("referencia").asText());
        assertEquals(0, new BigDecimal("100.00").compareTo(json.get("montoPago").decimalValue()));
    }

    @Test
    void productoCanjeable_catTipo10u11_telefonoConNumeralSinReferenciaNiMonto() throws Exception {
        for (int catTipo : new int[]{10, 11}) {
            JsonNode json = payload(request("ABC123"), producto(1, catTipo));

            assertEquals("#1111111111", json.get("telefono").asText());
            assertFalse(json.has("referencia"));
            assertFalse(json.has("montoPago"));
        }
    }

    @Test
    void upc_tiene14CaracteresAlfanumericosYNoSeRepite() {
        String upc1 = builder.generarUpc();
        String upc2 = builder.generarUpc();

        assertTrue(upc1.matches("[0-9A-Z]{14}"));
        assertNotEquals(upc1, upc2);
    }
}
