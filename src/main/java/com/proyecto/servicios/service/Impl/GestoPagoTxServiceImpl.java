package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoServiceClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.exception.CodigoErrorPago;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.gestopago.SendTxResponse;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.GestoPagoTxService;
import com.proyecto.servicios.util.AesEncryption;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.util.function.BiFunction;

@Service
@Slf4j
public class GestoPagoTxServiceImpl implements GestoPagoTxService {

    @Autowired
    private GestoPagoServiceClient gestoPagoServiceClient;

    @Autowired
    private GestoPagoTokenService tokenService;

    @Value("${gestopago.auth.id-distribuidor}")
    private Integer idDistribuidor;

    @Value("${gestopago.auth.codigo-dispositivo}")
    private String codigoDispositivo;

    @Value("${gestopago.crypto.key:}")
    private String cryptoKey;

    @Value("${gestopago.crypto.iv:}")
    private String cryptoIv;

    @Value("${gestopago.api-key:}")
    private String apiKey;

    @Override
    public SendTxResponse enviarTx(String payloadJson) {
        return invocar(payloadJson, (bearer, form) -> gestoPagoServiceClient.sendTxRaw(bearer, apiKeyONull(), form));
    }

    @Override
    public SendTxResponse confirmarTx(String payloadJson) {
        return invocar(payloadJson, (bearer, form) -> gestoPagoServiceClient.confirmTxRaw(bearer, apiKeyONull(), form));
    }

    private SendTxResponse invocar(String payloadJson, BiFunction<String, MultiValueMap<String, String>, String> llamada) {
        String signed = firmar(payloadJson);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("random", String.valueOf(System.currentTimeMillis()));
        form.add("signed", signed);

        String rawXml;
        try {
            rawXml = llamada.apply(bearer(obtenerToken()), form);
        } catch (FeignException.Forbidden e) {
            // 403 = token vencido: el proveedor no procesó la petición, por lo que es seguro renovar y reenviar una sola vez.
            log.warn("GestoPago respondió 403 (token vencido); se renueva el token y se reintenta una vez");
            tokenService.renovarTokenForzado();
            try {
                rawXml = llamada.apply(bearer(obtenerToken()), form);
            } catch (FeignException e2) {
                log.error("Error al llamar a GestoPago tras renovar el token: status={}", e2.status());
                throw new PagoException(CodigoErrorPago.GESTOPAGO_NO_DISPONIBLE);
            }
        } catch (FeignException e) {
            // Se registra solo el status: el mensaje original puede incluir URL y encabezados.
            log.error("Error al llamar a GestoPago: status={}", e.status());
            throw new PagoException(CodigoErrorPago.GESTOPAGO_NO_DISPONIBLE);
        }

        return parsearRespuesta(rawXml);
    }

    private GestoPagoToken obtenerToken() {
        return tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo)
                .orElseThrow(() -> new PagoException(CodigoErrorPago.GESTOPAGO_TOKEN_NO_DISPONIBLE));
    }

    private String bearer(GestoPagoToken token) {
        return token.getToken().startsWith("Bearer ") ? token.getToken() : "Bearer " + token.getToken();
    }

    private String apiKeyONull() {
        return apiKey == null || apiKey.isBlank() ? null : apiKey;
    }

    String firmar(String payloadJson) {
        if (cryptoKey == null || cryptoKey.isBlank() || cryptoIv == null || cryptoIv.isBlank()) {
            log.error("Falta configurar GESTOPAGO_CRYPTO_KEY / GESTOPAGO_CRYPTO_IV");
            throw new PagoException(CodigoErrorPago.FIRMA_NO_CONFIGURADA);
        }
        try {
            return AesEncryption.encrypt(payloadJson, cryptoIv, cryptoKey);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.error("No se pudo firmar el payload: {}", e.getClass().getSimpleName());
            throw new PagoException(CodigoErrorPago.FIRMA_NO_CONFIGURADA);
        }
    }

    SendTxResponse parsearRespuesta(String rawXml) {
        if (rawXml == null || rawXml.isBlank()) {
            throw new PagoException(CodigoErrorPago.GESTOPAGO_RESPUESTA_INVALIDA);
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(rawXml)));

            String codigo = texto(doc, "CODIGO");
            if (codigo == null) {
                throw new PagoException(CodigoErrorPago.GESTOPAGO_RESPUESTA_INVALIDA);
            }
            String leyenda = texto(doc, "TEXTO");
            if (leyenda == null) {
                leyenda = texto(doc, "TEXT");
            }
            if (leyenda == null) {
                leyenda = texto(doc, "legend");
            }
            return SendTxResponse.builder()
                    .codigo(Integer.parseInt(codigo.trim()))
                    .idTx(texto(doc, "ID_TX"))
                    .numAutorizacion(texto(doc, "NUM_AUTORIZACION"))
                    .saldo(decimal(texto(doc, "SALDO")))
                    .comision(decimal(texto(doc, "COMISION")))
                    .fecha(texto(doc, "FECHA"))
                    .monto(decimal(texto(doc, "MONTO")))
                    .referencia(texto(doc, "REFERENCIA"))
                    .pin(texto(doc, "PIN"))
                    .leyenda(leyenda)
                    .respuestaCruda(enmascararPin(rawXml))
                    .build();
        } catch (PagoException e) {
            throw e;
        } catch (Exception e) {
            log.error("No se pudo interpretar la respuesta de GestoPago: {}", e.getClass().getSimpleName());
            throw new PagoException(CodigoErrorPago.GESTOPAGO_RESPUESTA_INVALIDA);
        }
    }

    /** El PIN/folio de productos canjeables es sensible: no se guarda en la bitácora. */
    static String enmascararPin(String xml) {
        return xml.replaceAll("(?is)<PIN>.*?</PIN>", "<PIN>***</PIN>");
    }

    private String texto(Document doc, String tag) {
        NodeList nodes = doc.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        String value = nodes.item(0).getTextContent();
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** SALDO puede venir en notación científica; BigDecimal la interpreta. */
    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
