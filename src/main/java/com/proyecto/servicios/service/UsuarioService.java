package com.proyecto.servicios.service;

import com.proyecto.servicios.model.request.CambiaPasswordRequest;
import com.proyecto.servicios.model.response.UsuarioResponse;

public interface UsuarioService {

    UsuarioResponse obtenerUsuarioPorId(Long id);

    void cambiarPassword(Long id, CambiaPasswordRequest request);

    void darDeBajaLogicaUsuario(Long id);
}
