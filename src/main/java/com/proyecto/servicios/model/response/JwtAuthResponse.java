package com.proyecto.servicios.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtAuthResponse {
    private String accessToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private String correo;
    private Long usuarioId;
    private Long clienteId;
    /** ADMIN o CLIENTE: el frontend lo usa para decidir qué vistas mostrar. */
    private String rol;
}
