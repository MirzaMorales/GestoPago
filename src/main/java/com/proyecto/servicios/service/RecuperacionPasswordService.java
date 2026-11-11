package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.Usuario;
import com.proyecto.servicios.exception.Codigo2faInvalidoException;
import com.proyecto.servicios.model.request.RestablecerPasswordRequest;
import com.proyecto.servicios.model.response.Login2faResponse;
import com.proyecto.servicios.repositorys.UsuarioRepository;
import com.proyecto.servicios.security.LoginIntentosService;
import com.proyecto.servicios.security.Otp2faService;
import com.proyecto.servicios.security.SesionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * "Olvidé mi contraseña": código de un solo uso por correo (mismo resguardo que el 2FA) y contraseña nueva.
 * La respuesta del paso 1 es idéntica exista o no la cuenta, para no revelar qué correos están registrados.
 */
@Slf4j
@Service
public class RecuperacionPasswordService {

    private static final String CODIGO_INVALIDO = "El código expiró o no es válido. Solicita uno nuevo";

    private final UsuarioRepository usuarioRepository;
    private final Otp2faService otp;
    private final PasswordEncoder passwordEncoder;
    private final SesionService sesiones;
    private final LoginIntentosService loginIntentos;
    private final int ttlSegundos;

    public RecuperacionPasswordService(UsuarioRepository usuarioRepository, Otp2faService otp,
                                       PasswordEncoder passwordEncoder, SesionService sesiones,
                                       LoginIntentosService loginIntentos,
                                       @Value("${app.2fa.ttl-segundos:300}") int ttlSegundos) {
        this.usuarioRepository = usuarioRepository;
        this.otp = otp;
        this.passwordEncoder = passwordEncoder;
        this.sesiones = sesiones;
        this.loginIntentos = loginIntentos;
        this.ttlSegundos = ttlSegundos;
    }

    /** Envía el código si la cuenta existe y está activa; si no, responde igual sin enviar nada. */
    public Login2faResponse solicitar(String correoSolicitado) {
        String correo = correoSolicitado.trim().toLowerCase();
        Otp2faService.Desafio desafio = usuarioRepository.findByCorreo(correo)
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .map(u -> otp.crearRecuperacion(u.getId(), u.getCorreo()))
                .orElseGet(() -> {
                    log.info("Solicitud de recuperación para un correo no registrado o inactivo");
                    return new Otp2faService.Desafio(UUID.randomUUID().toString(), enmascarar(correo), ttlSegundos);
                });
        return Login2faResponse.builder()
                .requiere2fa(true)
                .desafioId(desafio.desafioId())
                .correoEnmascarado(desafio.correoEnmascarado())
                .expiraEnSegundos(desafio.expiraEnSegundos())
                .build();
    }

    /** Valida el código y cambia la contraseña; cierra cualquier sesión abierta y quita bloqueos de login. */
    @Transactional
    public void restablecer(RestablecerPasswordRequest request) {
        Otp2faService.Verificado verificado = otp.verificar(request.getDesafioId(), request.getCodigo());
        if (!Otp2faService.PROPOSITO_RECUPERACION.equals(verificado.plataforma())) {
            throw new Codigo2faInvalidoException(CODIGO_INVALIDO); // un código de login no restablece contraseñas
        }
        Usuario usuario = usuarioRepository.findById(verificado.usuarioId())
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .orElseThrow(() -> new Codigo2faInvalidoException(CODIGO_INVALIDO));

        usuario.setPassword(passwordEncoder.encode(request.getNuevaPassword()));
        usuarioRepository.save(usuario);
        sesiones.cerrarTodas(usuario.getId());
        loginIntentos.limpiar(usuario.getCorreo());
        log.info("Contraseña restablecida para el usuario {}", usuario.getId());
    }

    private static String enmascarar(String correo) {
        int arroba = correo.indexOf('@');
        return arroba <= 1 ? "***" + correo.substring(Math.max(arroba, 0)) : correo.charAt(0) + "***" + correo.substring(arroba);
    }
}
