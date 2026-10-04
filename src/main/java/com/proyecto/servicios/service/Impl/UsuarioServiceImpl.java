package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.Usuario;
import com.proyecto.servicios.exception.ContrasenaInvalidaException;
import com.proyecto.servicios.exception.UsuarioNoEncontradoException;
import com.proyecto.servicios.mapper.ClienteMapper;
import com.proyecto.servicios.model.request.CambiaPasswordRequest;
import com.proyecto.servicios.model.response.UsuarioResponse;
import com.proyecto.servicios.repositorys.UsuarioRepository;
import com.proyecto.servicios.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ClienteMapper clienteMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse obtenerUsuarioPorId(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNoEncontradoException("No se encontró el usuario con ID: " + id));
        return clienteMapper.toUsuarioResponse(usuario);
    }

    @Override
    @Transactional
    public void cambiarPassword(Long id, CambiaPasswordRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNoEncontradoException("No se encontró el usuario con ID: " + id));

        if (!passwordEncoder.matches(request.getPasswordActual(), usuario.getPassword())) {
            throw new ContrasenaInvalidaException("La contraseña actual proporcionada es incorrecta");
        }

        usuario.setPassword(passwordEncoder.encode(request.getNuevaPassword()));
        usuarioRepository.save(usuario);
    }

    @Override
    @Transactional
    public void darDeBajaLogicaUsuario(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNoEncontradoException("No se encontró el usuario con ID: " + id));

        usuario.setActivo(false);
        usuarioRepository.save(usuario);
    }
}
