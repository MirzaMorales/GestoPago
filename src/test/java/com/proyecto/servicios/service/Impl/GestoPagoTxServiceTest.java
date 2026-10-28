package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoServiceClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.exception.CodigoErrorPago;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.gestopago.SendTxResponse;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.util.AesEncryption;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

    // Llave AES-256 (32 bytes) e IV (16 bytes) de prueba generados para el test; NO son credenciales reales
    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String IV = Base64.getEncoder().encodeToString("fedcba9876543210".getBytes());
    private static final String PAYLOAD = "{\"idProducto\":582,\"idServicio\":133,\"telefono\":\"5555555551\"}";

    @Mock
    private GestoPagoServiceClient client;

    @Mock
    private GestoPagoTokenService tokenService;

    @InjectMocks
    private GestoPagoTxServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(service, "idDistribuidor", 83);
        ReflectionTestUtils.setField(service, "codigoDispositivo", "DEV");
        ReflectionTestUtils.setField(service, "cryptoKey", KEY);
        ReflectionTestUtils.setField(service, "cryptoIv", IV);
        ReflectionTestUtils.setField(service, "apiKey", "");
    }

    private GestoPagoToken token(String valor) {
        GestoPagoToken token = new GestoPagoToken();
        token.setToken(valor);
        return token;
    }

    private void conToken(String valor) {
        when(tokenService.obtenerTokenActivo(83, "DEV")).thenReturn(Optional.of(token(valor)));
    }

    // ---------- firma ----------

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

    // ---------- parseo de respuestas ----------

    @Test
    void parsearRespuesta_exitosaConEstructuraRealDelProveedor() {
        String xml = "<?xml version='1.0' encoding='UTF-8'?><RESPONSE><ID_TX>291676393</ID_TX><NUM_AUTORIZACION>65765544</NUM_AUTORIZACION>"
                + "<SALDO>1.5E3</SALDO><COMISION>2.5</COMISION><FECHA>25/Feb/2022 19:12:37</FECHA><MONTO>300.0</MONTO>"
                + "<MENSAJE><CODIGO>01</CODIGO><PIN>40919958</PIN><legend><![CDATA[Instrucciones <br> canje]]></legend>"
                + "<TEXTO>Operacion realizada con exito</TEXTO><REFERENCIA>ABC123</REFERENCIA></MENSAJE></RESPONSE>";

        SendTxResponse r = service.parsearRespuesta(xml);

        assertTrue(r.isExitoso());
        assertEquals("291676393", r.getIdTx());
        assertEquals("65765544", r.getNumAutorizacion());
        assertEquals(0, new BigDecimal("1500").compareTo(r.getSaldo()));
        assertEquals(0, new BigDecimal("300").compareTo(r.getMonto()));
        assertEquals("40919958", r.getPin());
    }

    @Test
    void parsearRespuesta_enmascaraElPinEnLaCopiaParaBitacora() {
        String xml = "<RESPONSE><MENSAJE><CODIGO>01</CODIGO><PIN>40919958</PIN></MENSAJE></RESPONSE>";

        SendTxResponse r = service.parsearRespuesta(xml);

        assertFalse(r.getRespuestaCruda().contains("40919958"));
        assertTrue(r.getRespuestaCruda().contains("<PIN>***</PIN>"));
    }

    @Test
    void parsearRespuesta_codigo82EsTimeout() {
        String xml = "<RESPONSE><NUM_AUTORIZACION>-1</NUM_AUTORIZACION><MENSAJE><CODIGO>82</CODIGO>"
                + "<TEXTO>Tiempo de espera alcanzado, la transacción no ha sido registrada</TEXTO></MENSAJE></RESPONSE>";

        SendTxResponse r = service.parsearRespuesta(xml);

        assertTrue(r.esTimeout());
        assertFalse(r.isExitoso());
    }

    @Test
    void parsearRespuesta_codigo06EsDuplicadoOAplicada() {
        SendTxResponse r = service.parsearRespuesta("<RESPONSE><MENSAJE><CODIGO>06</CODIGO><TEXTO>Transaccion aplicada anteriormente</TEXTO></MENSAJE></RESPONSE>");

        assertEquals(SendTxResponse.CODIGO_DUPLICADO_O_APLICADA, r.getCodigo());
        assertEquals("Transaccion aplicada anteriormente", r.getLeyenda());
    }

    @Test
    void parsearRespuesta_invalida_lanzaRespuestaInvalida() {
        for (String xml : new String[]{"", "no es xml", "<RESPONSE><OTRO>1</OTRO></RESPONSE>",
                "<!DOCTYPE a [<!ENTITY x \"y\">]><RESPONSE><CODIGO>01</CODIGO></RESPONSE>"}) {
            assertEquals(CodigoErrorPago.GESTOPAGO_RESPUESTA_INVALIDA,
                    assertThrows(PagoException.class, () -> service.parsearRespuesta(xml)).getError(), xml);
        }
    }

    // ---------- sendTx ----------

    @Test
    void enviarTx_sinToken_lanzaTokenNoDisponible() {
        when(tokenService.obtenerTokenActivo(83, "DEV")).thenReturn(Optional.empty());

        assertEquals(CodigoErrorPago.GESTOPAGO_TOKEN_NO_DISPONIBLE,
                assertThrows(PagoException.class, () -> service.enviarTx(PAYLOAD)).getError());
        verifyNoInteractions(client);
    }

    @Test
    void enviarTx_enviaFormConRandomYSignedCifradoYBearer() throws Exception {
        conToken("tok");
        when(client.sendTxRaw(eq("Bearer tok"), any(), any())).thenReturn("<R><CODIGO>01</CODIGO><ID_TX>7</ID_TX></R>");

        SendTxResponse r = service.enviarTx(PAYLOAD);

        assertEquals("7", r.getIdTx());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<MultiValueMap<String, String>> captor = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(client).sendTxRaw(eq("Bearer tok"), eq(null), captor.capture());
        assertNotNull(captor.getValue().getFirst("random"));
        assertEquals(PAYLOAD, AesEncryption.decrypt(captor.getValue().getFirst("signed"), IV, KEY));
    }

    @Test
    void enviarTx_conApiKeyConfigurada_laEnviaEnElHeader() {
        ReflectionTestUtils.setField(service, "apiKey", "mi-api-key");
        conToken("tok");
        when(client.sendTxRaw(any(), any(), any())).thenReturn("<R><CODIGO>01</CODIGO></R>");

        service.enviarTx(PAYLOAD);

        verify(client).sendTxRaw(eq("Bearer tok"), eq("mi-api-key"), any());
    }

    @Test
    void enviarTx_codigoDeError_devuelveLaRespuestaSinLanzar() {
        conToken("tok");
        when(client.sendTxRaw(any(), any(), any())).thenReturn("<R><MENSAJE><CODIGO>12</CODIGO><TEXTO>Rechazada</TEXTO></MENSAJE></R>");

        SendTxResponse r = service.enviarTx(PAYLOAD);

        assertEquals(12, r.getCodigo());
        assertFalse(r.isExitoso());
    }

    @Test
    void enviarTx_tokenVencido403_renuevaYReintentaUnaSolaVez() {
        conToken("viejo");
        when(client.sendTxRaw(eq("Bearer viejo"), any(), any())).thenThrow(mock(FeignException.Forbidden.class));
        doAnswer(inv -> {
            when(tokenService.obtenerTokenActivo(83, "DEV")).thenReturn(Optional.of(token("nuevo")));
            return null;
        }).when(tokenService).renovarTokenForzado();
        when(client.sendTxRaw(eq("Bearer nuevo"), any(), any())).thenReturn("<R><CODIGO>01</CODIGO></R>");

        SendTxResponse r = service.enviarTx(PAYLOAD);

        assertTrue(r.isExitoso());
        verify(tokenService, times(1)).renovarTokenForzado();
        verify(client, times(2)).sendTxRaw(any(), any(), any());
    }

    @Test
    void enviarTx_403PersistenteTrasRenovar_lanzaNoDisponible() {
        conToken("tok");
        when(client.sendTxRaw(any(), any(), any())).thenThrow(mock(FeignException.Forbidden.class));

        assertEquals(CodigoErrorPago.GESTOPAGO_NO_DISPONIBLE,
                assertThrows(PagoException.class, () -> service.enviarTx(PAYLOAD)).getError());
        verify(client, times(2)).sendTxRaw(any(), any(), any());
    }

    @Test
    void enviarTx_falloDeRed_lanzaNoDisponibleSinReintentar() {
        conToken("tok");
        when(client.sendTxRaw(any(), any(), any())).thenThrow(mock(FeignException.class));

        assertEquals(CodigoErrorPago.GESTOPAGO_NO_DISPONIBLE,
                assertThrows(PagoException.class, () -> service.enviarTx(PAYLOAD)).getError());
        verify(client, times(1)).sendTxRaw(any(), any(), any());
        verify(tokenService, never()).renovarTokenForzado();
    }

    // ---------- confirmTx ----------

    @Test
    void confirmarTx_usaConfirmTxConElMismoPayloadCifrado() throws Exception {
        conToken("tok");
        when(client.confirmTxRaw(any(), any(), any())).thenReturn(
                "<RESPONSE><NUM_AUTORIZACION>100024988</NUM_AUTORIZACION><MENSAJE><CODIGO>06</CODIGO>"
                        + "<TEXTO>La transaccion fue aplicada exitosamente</TEXTO><ID_TX>291437188</ID_TX></MENSAJE></RESPONSE>");

        SendTxResponse r = service.confirmarTx(PAYLOAD);

        assertEquals(6, r.getCodigo());
        assertEquals("291437188", r.getIdTx());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<MultiValueMap<String, String>> captor = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(client).confirmTxRaw(eq("Bearer tok"), any(), captor.capture());
        assertEquals(PAYLOAD, AesEncryption.decrypt(captor.getValue().getFirst("signed"), IV, KEY));
        verify(client, never()).sendTxRaw(any(), any(), any());
    }

    @Test
    void confirmarTx_noAplicada_codigo70() {
        conToken("tok");
        when(client.confirmTxRaw(any(), any(), any())).thenReturn(
                "<RESPONSE><NUM_AUTORIZACION>0</NUM_AUTORIZACION><MENSAJE><CODIGO>70</CODIGO><TEXTO>La transaccion no fue aplicada</TEXTO></MENSAJE></RESPONSE>");

        assertEquals(SendTxResponse.CODIGO_NO_APLICADA, service.confirmarTx(PAYLOAD).getCodigo());
    }
}
