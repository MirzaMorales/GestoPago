package com.proyecto.servicios.service;

import com.proyecto.servicios.model.request.LoginRequest;
import com.proyecto.servicios.model.response.JwtAuthResponse;

public interface AuthService {

    JwtAuthResponse login(LoginRequest request);

    /** Cierra la sesión del token recibido (JWT sin el prefijo "Bearer "). */
    void logout(String jwt);
}
