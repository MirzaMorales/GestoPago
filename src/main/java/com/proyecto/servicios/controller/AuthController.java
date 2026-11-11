package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.request.LoginRequest;
import com.proyecto.servicios.model.request.RestablecerPasswordRequest;
import com.proyecto.servicios.model.request.SolicitarRecuperacionRequest;
import com.proyecto.servicios.model.request.Verificacion2faRequest;
import com.proyecto.servicios.model.response.JwtAuthResponse;
import com.proyecto.servicios.model.response.Login2faResponse;
import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.RecuperacionPasswordService;
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

    @Autowired
    private RecuperacionPasswordService recuperacion;

    @PostMapping("/login")
    public ResponseEntity<Login2faResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Paso 2: valida el código 2FA enviado por correo y entrega el JWT. */
    @PostMapping("/verificar-2fa")
    public ResponseEntity<JwtAuthResponse> verificar2fa(@Valid @RequestBody Verificacion2faRequest request) {
        return ResponseEntity.ok(authService.verificar2fa(request));
    }

    /** Olvidé mi contraseña, paso 1: envía un código al correo (la respuesta no revela si la cuenta existe). */
    @PostMapping("/recuperar/solicitar")
    public ResponseEntity<Login2faResponse> solicitarRecuperacion(@Valid @RequestBody SolicitarRecuperacionRequest request) {
        return ResponseEntity.ok(recuperacion.solicitar(request.getCorreo()));
    }

    /** Olvidé mi contraseña, paso 2: valida el código y guarda la contraseña nueva; cierra sesiones abiertas. */
    @PostMapping("/recuperar/restablecer")
    public ResponseEntity<GenericResponse<Void>> restablecerPassword(@Valid @RequestBody RestablecerPasswordRequest request) {
        recuperacion.restablecer(request);
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK,
                "Contraseña restablecida. Inicia sesión con tu nueva contraseña", null));
    }

    /** Cierra la sesión activa del token enviado; requiere autenticación. */
    @PostMapping("/logout")
    public ResponseEntity<GenericResponse<Void>> logout(@RequestHeader("Authorization") String authorization) {
        authService.logout(authorization.substring(7));
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Sesión cerrada correctamente", null));
    }
}
