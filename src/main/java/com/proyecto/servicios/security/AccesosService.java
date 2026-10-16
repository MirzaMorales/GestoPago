package com.proyecto.servicios.security;

import com.proyecto.servicios.entity.Usuario;
import com.proyecto.servicios.repositorys.CuentaRepository;
import com.proyecto.servicios.repositorys.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Reglas de propiedad de recursos, usadas desde @PreAuthorize("... @accesos.xxx(...)").
 * Los ADMIN se validan con hasRole('ADMIN') en la propia anotación.
 */
@Component("accesos")
public class AccesosService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    public boolean esClienteId(Long clienteId) {
        return usuarioActual()
                .map(u -> u.getCliente() != null && u.getCliente().getId().equals(clienteId))
                .orElse(false);
    }

    public boolean esUsuarioId(Long usuarioId) {
        return usuarioActual().map(u -> u.getId().equals(usuarioId)).orElse(false);
    }

    public boolean esDuenoDeCuenta(String numeroCuenta) {
        return usuarioActual()
                .filter(u -> u.getCliente() != null)
                .flatMap(u -> cuentaRepository.findByNumeroCuenta(numeroCuenta)
                        .map(c -> c.getCliente().getId().equals(u.getCliente().getId())))
                .orElse(false);
    }

    private Optional<Usuario> usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof String correo)) {
            return Optional.empty();
        }
        return usuarioRepository.findByCorreo(correo);
    }
}
