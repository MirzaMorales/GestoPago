package com.proyecto.servicios.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class GestoPagoClientConfigTest {

    private GestoPagoClientConfig config(String apiKey) {
        GestoPagoClientConfig c = new GestoPagoClientConfig();
        ReflectionTestUtils.setField(c, "urlAutenticacion", "https://gestopago.portalventas.net");
        ReflectionTestUtils.setField(c, "urlServicio", "https://gestopago.portalventas.net");
        ReflectionTestUtils.setField(c, "apiKey", apiKey);
        return c;
    }

    @Test
    void agregaLaApiKeyATodaPeticionHaciaElProveedor() {
        RequestInterceptor interceptor = config("mi-api-key").gestoPagoApiKeyInterceptor();
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertEquals("mi-api-key", template.headers().get("X-API-Key").iterator().next());
    }

    @Test
    void noDuplicaLaApiKeySiLaPeticionYaLaTrae() {
        RequestInterceptor interceptor = config("mi-api-key").gestoPagoApiKeyInterceptor();
        RequestTemplate template = new RequestTemplate().header("X-API-Key", "mi-api-key");

        interceptor.apply(template);

        assertEquals(1, template.headers().get("X-API-Key").size());
    }

    @Test
    void sinApiKeyConfiguradaNoSeEnviaElHeader() {
        RequestTemplate template = new RequestTemplate();

        config("").gestoPagoApiKeyInterceptor().apply(template);

        assertFalse(template.headers().containsKey("X-API-Key"));
    }

    @Test
    void lasUrlsDelProveedorDebenSerHttps() {
        assertDoesNotThrow(() -> config("k").validarConfiguracion());

        GestoPagoClientConfig insegura = config("k");
        ReflectionTestUtils.setField(insegura, "urlServicio", "http://gestopago.portalventas.net");
        IllegalStateException ex = assertThrows(IllegalStateException.class, insegura::validarConfiguracion);
        assertTrue(ex.getMessage().contains("https://"));
    }
}
