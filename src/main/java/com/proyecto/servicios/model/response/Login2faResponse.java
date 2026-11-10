package com.proyecto.servicios.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Respuesta del primer paso del login: credenciales válidas, falta el código enviado por correo. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Login2faResponse {
    /** Siempre true: el acceso solo se concede con POST /auth/verificar-2fa. */
    private boolean requiere2fa;
    private String desafioId;
    /** Ej.: u***@gmail.com */
    private String correoEnmascarado;
    private int expiraEnSegundos;
}
