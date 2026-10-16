package com.proyecto.servicios.config;

import com.proyecto.servicios.entity.Rol;
import com.proyecto.servicios.entity.Usuario;
import com.proyecto.servicios.repositorys.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea el usuario ADMIN inicial si se definen APP_ADMIN_CORREO y APP_ADMIN_PASSWORD.
 * La contraseña no se guarda en el código: se toma de variables de entorno.
 */
@Slf4j
@Component
public class AdminInitializer implements ApplicationRunner {

    @Value("${app.admin.correo:}")
    private String correo;

    @Value("${app.admin.password:}")
    private String password;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (correo.isBlank() || password.isBlank()) {
            return;
        }
        String correoNormalizado = correo.trim().toLowerCase();
        if (usuarioRepository.existsByCorreo(correoNormalizado)) {
            return;
        }
        usuarioRepository.save(Usuario.builder()
                .correo(correoNormalizado)
                .password(passwordEncoder.encode(password))
                .rol(Rol.ADMIN)
                .activo(true)
                .build());
        log.info("Usuario ADMIN inicial creado: {}", correoNormalizado);
    }
}
