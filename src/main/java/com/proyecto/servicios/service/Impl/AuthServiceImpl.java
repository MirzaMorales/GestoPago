package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.Rol;
import com.proyecto.servicios.entity.Usuario;
import com.proyecto.servicios.exception.Codigo2faInvalidoException;
import com.proyecto.servicios.exception.CredencialesInvalidasException;
import com.proyecto.servicios.exception.UsuarioInactivoException;
import com.proyecto.servicios.model.request.LoginRequest;
import com.proyecto.servicios.model.request.Verificacion2faRequest;
import com.proyecto.servicios.model.response.JwtAuthResponse;
import com.proyecto.servicios.model.response.Login2faResponse;
import com.proyecto.servicios.repositorys.UsuarioRepository;
import com.proyecto.servicios.security.JwtTokenProvider;
import com.proyecto.servicios.security.LoginIntentosService;
import com.proyecto.servicios.security.Otp2faService;
import com.proyecto.servicios.security.SesionService;
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

    @Autowired
    private SesionService sesionService;

    @Autowired
    private Otp2faService otp2faService;

    @Autowired
    private LoginIntentosService loginIntentos;

    /** Paso 1: valida credenciales y la sesión única, y envía el código 2FA por correo. No entrega token. */
    @Override
    @Transactional(readOnly = true)
    public Login2faResponse login(LoginRequest request) {
        String correo = request.getCorreo().trim().toLowerCase();

        loginIntentos.verificarNoBloqueado(correo);

        Usuario usuario = usuarioRepository.findByCorreo(correo).orElse(null);
        if (usuario == null) {
            loginIntentos.registrarFallo(correo);
            throw new CredencialesInvalidasException("Credenciales de acceso inválidas");
        }

        // La contraseña se valida primero: un usuario inactivo no debe poder detectarse sin conocer sus credenciales.
        if (!passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
            loginIntentos.registrarFallo(correo);
            throw new CredencialesInvalidasException("Credenciales de acceso inválidas");
        }
        loginIntentos.limpiar(correo);

        validarUsuarioActivo(usuario);

        String plataforma = request.getPlataforma() != null ? request.getPlataforma() : "WEB";
        boolean cerrarAnterior = Boolean.TRUE.equals(request.getCerrarSesionAnterior());

        // Se avisa de la sesión activa ANTES de enviar el código, para no gastar un 2FA en un acceso que se va a rechazar.
        sesionService.verificarSinSesionActiva(usuario.getId(), cerrarAnterior);

        Otp2faService.Desafio desafio = otp2faService.crear(
                usuario.getId(), usuario.getCorreo(), plataforma, cerrarAnterior);

        return Login2faResponse.builder()
                .requiere2fa(true)
                .desafioId(desafio.desafioId())
                .correoEnmascarado(desafio.correoEnmascarado())
                .expiraEnSegundos(desafio.expiraEnSegundos())
                .build();
    }

    /** Paso 2: valida el código en el servidor, abre la sesión única y entrega el JWT. */
    @Override
    @Transactional(readOnly = true)
    public JwtAuthResponse verificar2fa(Verificacion2faRequest request) {
        Otp2faService.Verificado verificado = otp2faService.verificar(request.getDesafioId(), request.getCodigo());
        // Un código de recuperación de contraseña nunca abre sesión.
        if (Otp2faService.PROPOSITO_RECUPERACION.equals(verificado.plataforma())) {
            throw new Codigo2faInvalidoException("El código expiró o no es válido. Inicie sesión nuevamente");
        }

        Usuario usuario = usuarioRepository.findById(verificado.usuarioId())
                .orElseThrow(() -> new Codigo2faInvalidoException("El código expiró o no es válido. Inicie sesión nuevamente"));
        validarUsuarioActivo(usuario);

        Long clienteId = usuario.getCliente() != null ? usuario.getCliente().getId() : null;
        String jti = sesionService.iniciar(usuario.getId(), verificado.plataforma(), verificado.cerrarSesionAnterior());
        String token = jwtTokenProvider.generateToken(usuario.getCorreo(), usuario.getId(), clienteId, jti);

        return JwtAuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .correo(usuario.getCorreo())
                .usuarioId(usuario.getId())
                .clienteId(clienteId)
                .rol(usuario.getRol().name())
                .build();
    }

    @Override
    public void logout(String jwt) {
        sesionService.cerrar(jwtTokenProvider.getUsuarioIdFromToken(jwt), jwtTokenProvider.getJtiFromToken(jwt));
    }

    private void validarUsuarioActivo(Usuario usuario) {
        if (Boolean.FALSE.equals(usuario.getActivo())) {
            throw new UsuarioInactivoException("El usuario se encuentra inactivo en el sistema");
        }
        if (usuario.getRol() != Rol.ADMIN
                && (usuario.getCliente() == null || !Boolean.TRUE.equals(usuario.getCliente().getActivo()))) {
            throw new UsuarioInactivoException("El cliente asociado al usuario se encuentra inactivo");
        }
    }
}
