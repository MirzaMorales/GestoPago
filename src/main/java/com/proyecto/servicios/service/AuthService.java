package com.proyecto.servicios.service;

import com.proyecto.servicios.model.request.LoginRequest;
import com.proyecto.servicios.model.response.JwtAuthResponse;

public interface AuthService {

    JwtAuthResponse login(LoginRequest request);
}
