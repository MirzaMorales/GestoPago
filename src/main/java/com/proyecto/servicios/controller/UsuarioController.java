package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.request.CambiaPasswordRequest;
import com.proyecto.servicios.model.response.UsuarioResponse;
import com.proyecto.servicios.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> obtenerUsuarioPorId(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.obtenerUsuarioPorId(id));
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> cambiarPassword(
            @PathVariable Long id,
            @Valid @RequestBody CambiaPasswordRequest request
    ) {
        usuarioService.cambiarPassword(id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> darDeBajaLogicaUsuario(@PathVariable Long id) {
        usuarioService.darDeBajaLogicaUsuario(id);
        return ResponseEntity.noContent().build();
    }
}
