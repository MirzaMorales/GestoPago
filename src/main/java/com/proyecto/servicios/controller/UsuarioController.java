package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.request.CambiaPasswordRequest;
import com.proyecto.servicios.model.request.UsuarioIdRequest;
import com.proyecto.servicios.model.response.UsuarioResponse;
import com.proyecto.servicios.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @PostMapping("/consultar")
    @PreAuthorize("hasRole('ADMIN') or @accesos.esUsuarioId(#request.id)")
    public ResponseEntity<GenericResponse<UsuarioResponse>> obtenerUsuarioPorId(@Valid @RequestBody UsuarioIdRequest request) {
        UsuarioResponse usuario = usuarioService.obtenerUsuarioPorId(request.getId());
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Consulta realizada correctamente", usuario));
    }

    @PutMapping("/password")
    @PreAuthorize("hasRole('ADMIN') or @accesos.esUsuarioId(#request.id)")
    public ResponseEntity<GenericResponse<Void>> cambiarPassword(@Valid @RequestBody CambiaPasswordRequest request) {
        usuarioService.cambiarPassword(request);
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Contraseña actualizada correctamente", null));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GenericResponse<Void>> darDeBajaLogicaUsuario(@Valid @RequestBody UsuarioIdRequest request) {
        usuarioService.darDeBajaLogicaUsuario(request.getId());
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Usuario dado de baja correctamente", null));
    }
}
