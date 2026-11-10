package com.proyecto.servicios.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiTest {

    private final OpenAPI api = new OpenApi().openAPI();

    @Test
    void declaraElEsquemaBearerJwtParaQueSwaggerMuestreAuthorize() {
        SecurityScheme esquema = api.getComponents().getSecuritySchemes().get("bearerAuth");

        assertEquals(SecurityScheme.Type.HTTP, esquema.getType());
        assertEquals("bearer", esquema.getScheme());
        assertEquals("JWT", esquema.getBearerFormat());
        assertTrue(api.getSecurity().get(0).containsKey("bearerAuth"));
    }

    @Test
    void noFijaUnServidorLocalParaQueTryItOutUseElMismoOrigen() {
        assertTrue(api.getServers() == null || api.getServers().isEmpty());
    }

    @Test
    void esUnaClaseDeConfiguracionActiva() {
        assertNotNull(OpenApi.class.getAnnotation(Configuration.class));
    }
}
