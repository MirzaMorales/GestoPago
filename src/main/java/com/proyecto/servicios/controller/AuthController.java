package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.request.LoginRequest;
import com.proyecto.servicios.model.response.JwtAuthResponse;
import com.proyecto.servicios.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<JwtAuthResponse> login(@Valid @RequestBody LoginRequest request) {
        JwtAuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /** Cierra la sesión activa del token enviado; requiere autenticación. */
    @PostMapping("/logout")
    public ResponseEntity<GenericResponse<Void>> logout(@RequestHeader("Authorization") String authorization) {
        authService.logout(authorization.substring(7));
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Sesión cerrada correctamente", null));
    }
}
