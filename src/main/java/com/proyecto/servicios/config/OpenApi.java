package com.proyecto.servicios.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación Swagger: declara el esquema Bearer JWT para que aparezca el botón "Authorize" y las
 * peticiones de prueba lleven el token. No fija servidor: Swagger usa el mismo origen desde el que se abre.
 */
@Configuration
public class OpenApi {

    static final String ESQUEMA_BEARER = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("GestoPago API")
                        .description("Acceso con 2FA (POST /auth/login y /auth/verificar-2fa), sesión única, "
                                + "catálogo, compras e historial. Tras verificar el 2FA pulse Authorize y pegue el accessToken.")
                        .version("1.0"))
                .components(new Components().addSecuritySchemes(ESQUEMA_BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_BEARER));
    }
}
