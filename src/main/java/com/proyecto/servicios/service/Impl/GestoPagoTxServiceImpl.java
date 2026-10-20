package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.GestoPagoServiceClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.exception.CodigoErrorPago;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.gestopago.SendTxResponse;
import com.proyecto.servicios.model.request.PagoServicioRequest;
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
import java.security.SecureRandom;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class GestoPagoTxServiceImpl implements GestoPagoTxService {

    private static final DateTimeFormatter HORA_LOCAL = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String TELEFONO_FIJO_TIPO_FRONT_2 = "1111111111";
    private static final String TELEFONO_FIJO_SERVICIOS = "#1111111111";
    private static final String BASE36 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private GestoPagoServiceClient gestoPagoServiceClient;

    @Autowired
    private GestoPagoTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${gestopago.auth.id-distribuidor}")
    private Integer idDistribuidor;

    @Value("${gestopago.auth.codigo-dispositivo}")
    private String codigoDispositivo;

    @Value("${gestopago.crypto.key:}")
    private String cryptoKey;

    @Value("${gestopago.crypto.iv:}")
    private String cryptoIv;

    @Value("${gestopago.tx.unidad:083}")
    private String unidad;

    @Value("${gestopago.tx.zona-horaria:America/Mexico_City}")
    private String zonaHoraria;

    @Override
    public SendTxResponse enviarTx(PagoServicioRequest request, ProductoEntity producto) {
        GestoPagoToken token = tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo)
                .orElseThrow(() -> new PagoException(CodigoErrorPago.GESTOPAGO_TOKEN_NO_DISPONIBLE));

        String signed = firmar(construirPayload(request, producto));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("random", String.valueOf(System.currentTimeMillis()));
        form.add("signed", signed);

        String bearer = token.getToken().startsWith("Bearer ") ? token.getToken() : "Bearer " + token.getToken();
        String rawXml;
        try {
            rawXml = gestoPagoServiceClient.sendTxRaw(bearer, form);
        } catch (FeignException e) {
            log.error("Error al llamar sendTx de GestoPago: status={}", e.status());
            throw new PagoException(CodigoErrorPago.GESTOPAGO_NO_DISPONIBLE);
        }

        SendTxResponse respuesta = parsearRespuesta(rawXml);
        if (!respuesta.isExitoso()) {
            log.warn("GestoPago rechazó la transacción: codigo={}", respuesta.getCodigo());
            throw new PagoException(CodigoErrorPago.GESTOPAGO_RECHAZADO,
                    "CODIGO " + respuesta.getCodigo() + (respuesta.getLeyenda() != null ? ": " + respuesta.getLeyenda() : ""));
        }
        return respuesta;
    }

    /**
     * JSON de sendTx. El campo telefono depende de tipoFront / idCatTipoServicio; cuando el
     * telefono es un valor fijo, la referencia y el monto viajan en referencia/montoPago
     * (pago de servicios).
     */
    String construirPayload(PagoServicioRequest request, ProductoEntity producto) {
        Integer catTipo = producto.getIdCatTipoServicio();
        boolean esServicio = (catTipo != null && (catTipo == 10 || catTipo == 11));

        String telefono;
        if (esServicio) {
            telefono = TELEFONO_FIJO_SERVICIOS;
        } else if (Integer.valueOf(2).equals(producto.getTipoFront())) {
            telefono = TELEFONO_FIJO_TIPO_FRONT_2;
        } else {
            telefono = request.getTelefonoReferencia();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("idProducto", producto.getIdProducto());
        payload.put("idServicio", producto.getIdServicio());
        payload.put("telefono", telefono);
        payload.put("horaLocal", ZonedDateTime.now(ZoneId.of(zonaHoraria)).format(HORA_LOCAL));
        if (!telefono.equals(request.getTelefonoReferencia())) {
            payload.put("referencia", request.getTelefonoReferencia());
            payload.put("montoPago", request.getMonto());
        }
        payload.put("upc", generarUpc());
        payload.put("unidad", unidad);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new PagoException(CodigoErrorPago.GESTOPAGO_RESPUESTA_INVALIDA);
        }
    }

    String firmar(String payloadJson) {
        if (cryptoKey == null || cryptoKey.isBlank() || cryptoIv == null || cryptoIv.isBlank()) {
            log.error("Falta configurar GESTOPAGO_CRYPTO_KEY / GESTOPAGO_CRYPTO_IV");
            throw new PagoException(CodigoErrorPago.FIRMA_NO_CONFIGURADA);
        }
        try {
            return AesEncryption.encrypt(payloadJson, cryptoIv, cryptoKey);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.error("No se pudo firmar el payload de sendTx: {}", e.getClass().getSimpleName());
            throw new PagoException(CodigoErrorPago.FIRMA_NO_CONFIGURADA);
        }
    }

    /** Identificador único de operación: 14 caracteres alfanuméricos (timestamp base36 + aleatorio). */
    String generarUpc() {
        StringBuilder upc = new StringBuilder(Long.toString(System.currentTimeMillis(), 36).toUpperCase());
        while (upc.length() < 14) {
            upc.append(BASE36.charAt(RANDOM.nextInt(BASE36.length())));
        }
        return upc.substring(0, 14);
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
                    .build();
        } catch (PagoException e) {
            throw e;
        } catch (Exception e) {
            log.error("No se pudo interpretar la respuesta de sendTx: {}", e.getClass().getSimpleName());
            throw new PagoException(CodigoErrorPago.GESTOPAGO_RESPUESTA_INVALIDA);
        }
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
