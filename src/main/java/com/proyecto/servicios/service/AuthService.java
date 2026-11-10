package com.proyecto.servicios.service;

import com.proyecto.servicios.model.request.LoginRequest;
import com.proyecto.servicios.model.request.Verificacion2faRequest;
import com.proyecto.servicios.model.response.JwtAuthResponse;
import com.proyecto.servicios.model.response.Login2faResponse;

public interface AuthService {

    /** Paso 1 del acceso: valida credenciales y envía el código 2FA. No entrega token. */
    Login2faResponse login(LoginRequest request);

    /** Paso 2 del acceso: valida el código 2FA y entrega el JWT con la sesión única abierta. */
    JwtAuthResponse verificar2fa(Verificacion2faRequest request);

    /** Cierra la sesión del token recibido (JWT sin el prefijo "Bearer "). */
    void logout(String jwt);
}
