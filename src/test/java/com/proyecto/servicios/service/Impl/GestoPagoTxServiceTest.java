package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.GestoPagoServiceClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.exception.CodigoErrorPago;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.gestopago.SendTxResponse;
import com.proyecto.servicios.model.request.PagoServicioRequest;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.util.AesEncryption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GestoPagoTxServiceTest {

    // Llave AES-128 e IV de 16 bytes de prueba (NO son credenciales reales)
    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes());
    private static final String IV = Base64.getEncoder().encodeToString("fedcba9876543210".getBytes());

    @Mock
    private GestoPagoServiceClient client;

    @Mock
    private GestoPagoTokenService tokenService;

    @InjectMocks
    private GestoPagoTxServiceImpl service;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(service, "objectMapper", mapper);
        ReflectionTestUtils.setField(service, "idDistribuidor", 83);
        ReflectionTestUtils.setField(service, "codigoDispositivo", "DEV");
        ReflectionTestUtils.setField(service, "cryptoKey", KEY);
        ReflectionTestUtils.setField(service, "cryptoIv", IV);
        ReflectionTestUtils.setField(service, "unidad", "083");
        ReflectionTestUtils.setField(service, "zonaHoraria", "America/Mexico_City");
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
        return mapper.readTree(service.construirPayload(request, producto));
    }

    @Test
    void payload_tipoFront1_enviaTelefonoDelRequestSinMontoNiReferencia() throws Exception {
        JsonNode json = payload(request("5555555551"), producto(1, 1));

        assertEquals("5555555551", json.get("telefono").asText());
        assertEquals(582, json.get("idProducto").asInt());
        assertEquals(133, json.get("idServicio").asInt());
        assertEquals("083", json.get("unidad").asText());
        assertFalse(json.has("referencia"));
        assertFalse(json.has("montoPago"));
        assertTrue(json.get("horaLocal").asText().matches("\\d{14}"));
    }

    @Test
    void payload_tipoFront2_telefonoFijoYReferenciaConMonto() throws Exception {
        JsonNode json = payload(request("ABC123"), producto(2, 1));

        assertEquals("1111111111", json.get("telefono").asText());
        assertEquals("ABC123", json.get("referencia").asText());
        assertEquals(0, new BigDecimal("100.00").compareTo(json.get("montoPago").decimalValue()));
    }

    @Test
    void payload_catTipoServicio10u11_telefonoConNumeral() throws Exception {
        assertEquals("#1111111111", payload(request("ABC123"), producto(1, 10)).get("telefono").asText());
        assertEquals("#1111111111", payload(request("ABC123"), producto(2, 11)).get("telefono").asText());
    }

    @Test
    void upc_tiene14CaracteresAlfanumericosYNoSeRepite() {
        String upc1 = service.generarUpc();
        String upc2 = service.generarUpc();

        assertTrue(upc1.matches("[0-9A-Z]{14}"));
        assertNotEquals(upc1, upc2);
    }

    @Test
    void firmar_cifraAesYSePuedeDescifrar() throws Exception {
        String signed = service.firmar("{\"a\":1}");

        assertNotEquals("{\"a\":1}", signed);
        assertEquals("{\"a\":1}", AesEncryption.decrypt(signed, IV, KEY));
    }

    @Test
    void firmar_sinLlaveConfigurada_lanzaFirmaNoConfigurada() {
        ReflectionTestUtils.setField(service, "cryptoKey", "");

        assertEquals(CodigoErrorPago.FIRMA_NO_CONFIGURADA,
                assertThrows(PagoException.class, () -> service.firmar("{}")).getError());
    }

    @Test
    void firmar_conLlaveInvalida_lanzaFirmaNoConfigurada() {
        ReflectionTestUtils.setField(service, "cryptoKey", Base64.getEncoder().encodeToString("corta".getBytes()));

        assertEquals(CodigoErrorPago.FIRMA_NO_CONFIGURADA,
                assertThrows(PagoException.class, () -> service.firmar("{}")).getError());
    }

    @Test
    void parsearRespuesta_exitosa_mapeaCampos() {
        String xml = "<RESPONSE><CODIGO>01</CODIGO><ID_TX>999</ID_TX><NUM_AUTORIZACION>AUT123</NUM_AUTORIZACION>"
                + "<SALDO>1.5E3</SALDO><COMISION>2.5</COMISION><FECHA>09/11/2010 13:15:26</FECHA>"
                + "<MONTO>100.0</MONTO><REFERENCIA>ABC123</REFERENCIA></RESPONSE>";

        SendTxResponse r = service.parsearRespuesta(xml);

        assertTrue(r.isExitoso());
        assertEquals("999", r.getIdTx());
        assertEquals("AUT123", r.getNumAutorizacion());
        assertEquals(0, new BigDecimal("1500").compareTo(r.getSaldo()));
        assertEquals(0, new BigDecimal("100").compareTo(r.getMonto()));
    }

    @Test
    void parsearRespuesta_codigoDistintoDe01_noEsExitosa() {
        SendTxResponse r = service.parsearRespuesta("<RESPONSE><CODIGO>12</CODIGO><TEXTO>Saldo insuficiente</TEXTO></RESPONSE>");

        assertFalse(r.isExitoso());
        assertEquals("Saldo insuficiente", r.getLeyenda());
    }

    @Test
    void parsearRespuesta_invalida_lanzaRespuestaInvalida() {
        for (String xml : new String[]{"", "no es xml", "<RESPONSE><OTRO>1</OTRO></RESPONSE>",
                "<!DOCTYPE a [<!ENTITY x \"y\">]><RESPONSE><CODIGO>01</CODIGO></RESPONSE>"}) {
            assertEquals(CodigoErrorPago.GESTOPAGO_RESPUESTA_INVALIDA,
                    assertThrows(PagoException.class, () -> service.parsearRespuesta(xml)).getError(), xml);
        }
    }

    @Test
    void enviarTx_sinToken_lanzaTokenNoDisponible() {
        when(tokenService.obtenerTokenActivo(83, "DEV")).thenReturn(Optional.empty());

        assertEquals(CodigoErrorPago.GESTOPAGO_TOKEN_NO_DISPONIBLE,
                assertThrows(PagoException.class, () -> service.enviarTx(request("5555555551"), producto(1, 1))).getError());
        verifyNoInteractions(client);
    }

    @Test
    void enviarTx_exitoso_enviaFormConRandomYSignedConBearer() {
        GestoPagoToken token = new GestoPagoToken();
        token.setToken("tok");
        when(tokenService.obtenerTokenActivo(83, "DEV")).thenReturn(Optional.of(token));
        when(client.sendTxRaw(eq("Bearer tok"), any())).thenReturn("<R><CODIGO>01</CODIGO><ID_TX>7</ID_TX></R>");

        SendTxResponse r = service.enviarTx(request("5555555551"), producto(1, 1));

        assertEquals("7", r.getIdTx());
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<MultiValueMap<String, String>> captor = org.mockito.ArgumentCaptor.forClass(MultiValueMap.class);
        verify(client).sendTxRaw(eq("Bearer tok"), captor.capture());
        assertNotNull(captor.getValue().getFirst("random"));
        assertNotNull(captor.getValue().getFirst("signed"));
    }

    @Test
    void enviarTx_codigoDeError_lanzaRechazadoConDetalle() {
        GestoPagoToken token = new GestoPagoToken();
        token.setToken("tok");
        when(tokenService.obtenerTokenActivo(83, "DEV")).thenReturn(Optional.of(token));
        when(client.sendTxRaw(any(), any())).thenReturn("<R><CODIGO>12</CODIGO><TEXTO>Rechazada</TEXTO></R>");

        PagoException ex = assertThrows(PagoException.class,
                () -> service.enviarTx(request("5555555551"), producto(1, 1)));

        assertEquals(CodigoErrorPago.GESTOPAGO_RECHAZADO, ex.getError());
        assertEquals("CODIGO 12: Rechazada", ex.getDetalle());
    }

    @Test
    void enviarTx_falloDeRed_lanzaNoDisponible() {
        GestoPagoToken token = new GestoPagoToken();
        token.setToken("tok");
        when(tokenService.obtenerTokenActivo(83, "DEV")).thenReturn(Optional.of(token));
        when(client.sendTxRaw(any(), any())).thenThrow(mock(feign.FeignException.class));

        assertEquals(CodigoErrorPago.GESTOPAGO_NO_DISPONIBLE,
                assertThrows(PagoException.class, () -> service.enviarTx(request("5555555551"), producto(1, 1))).getError());
    }
}
