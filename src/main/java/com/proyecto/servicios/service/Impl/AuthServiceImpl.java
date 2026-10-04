package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.Usuario;
import com.proyecto.servicios.exception.CredencialesInvalidasException;
import com.proyecto.servicios.exception.UsuarioInactivoException;
import com.proyecto.servicios.model.request.LoginRequest;
import com.proyecto.servicios.model.response.JwtAuthResponse;
import com.proyecto.servicios.repositorys.UsuarioRepository;
import com.proyecto.servicios.security.JwtTokenProvider;
import com.proyecto.servicios.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional(readOnly = true)
    public JwtAuthResponse login(LoginRequest request) {
        String correo = request.getCorreo().trim().toLowerCase();

        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new CredencialesInvalidasException("Credenciales de acceso inválidas"));

        if (Boolean.FALSE.equals(usuario.getActivo())) {
            throw new UsuarioInactivoException("El usuario se encuentra inactivo en el sistema");
        }

        if (usuario.getCliente() == null || !Boolean.TRUE.equals(usuario.getCliente().getActivo())) {
            throw new UsuarioInactivoException("El cliente asociado al usuario se encuentra inactivo");
        }

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
            throw new CredencialesInvalidasException("Credenciales de acceso inválidas");
        }

        Long clienteId = usuario.getCliente() != null ? usuario.getCliente().getId() : null;
        String token = jwtTokenProvider.generateToken(usuario.getCorreo(), usuario.getId(), clienteId);

        return JwtAuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .correo(usuario.getCorreo())
                .usuarioId(usuario.getId())
                .clienteId(clienteId)
                .build();
    }
}
