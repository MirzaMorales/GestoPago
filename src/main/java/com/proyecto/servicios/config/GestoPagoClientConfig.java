package com.proyecto.servicios.config;

import feign.RequestInterceptor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seguridad de las peticiones hacia GestoPago:
 * <ul>
 *   <li>Solo se habla con el proveedor por HTTPS (TLS con validación del certificado del servidor);
 *       si una URL configurada no es https el servicio no arranca.</li>
 *   <li>El header X-API-Key asignado al integrador viaja en TODAS las peticiones (autenticación, catálogo,
 *       sendTx y confirmTx), como lo muestra la documentación del proveedor en cada método.</li>
 * </ul>
 * El payload de sendTx/confirmTx va además cifrado con AES-256-CBC (ver GestoPagoTxServiceImpl).
 */
@Slf4j
@Configuration
public class GestoPagoClientConfig {

    static final String HEADER_API_KEY = "X-API-Key";

    @Value("${gestopago.auth.url}")
    private String urlAutenticacion;

    @Value("${gestopago.service.url}")
    private String urlServicio;

    @Value("${gestopago.api-key:}")
    private String apiKey;

    @PostConstruct
    void validarConfiguracion() {
        exigirHttps("gestopago.auth.url", urlAutenticacion);
        exigirHttps("gestopago.service.url", urlServicio);
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GESTOPAGO_API_KEY no está configurada: las peticiones al proveedor irán sin X-API-Key");
        }
    }

    static void exigirHttps(String propiedad, String url) {
        if (url == null || !url.toLowerCase().startsWith("https://")) {
            throw new IllegalStateException(propiedad + " debe usar https:// (el proveedor exige conexión cifrada con TLS)");
        }
    }

    /** Agrega X-API-Key a cada petición Feign hacia el proveedor (si no la trae ya y hay una configurada). */
    @Bean
    public RequestInterceptor gestoPagoApiKeyInterceptor() {
        return template -> {
            if (apiKey != null && !apiKey.isBlank() && !template.headers().containsKey(HEADER_API_KEY)) {
                template.header(HEADER_API_KEY, apiKey);
            }
        };
    }
}
