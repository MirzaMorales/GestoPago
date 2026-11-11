package com.proyecto.servicios.security;

import com.proyecto.servicios.exception.SesionActivaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Administración centralizada de sesiones: una sola sesión activa por usuario (web o móvil).
 * Se guarda en Redis {@code sesion:usuario:{id} = jti|PLATAFORMA} con el mismo TTL que el JWT,
 * de modo que la sesión expira sola y un token cuyo jti ya no coincide deja de ser válido.
 */
@Service
public class SesionService {

    private static final String PREFIJO = "sesion:usuario:";
    private static final String SEPARADOR = "|";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public SesionService(StringRedisTemplate redis,
                         @Value("${app.jwt.expiration-ms:3600000}") long expiracionMs) {
        this.redis = redis;
        this.ttl = Duration.ofMillis(expiracionMs);
    }

    /**
     * Abre la sesión del usuario y devuelve su identificador (jti).
     *
     * @param reemplazar true para cerrar la sesión anterior; false para rechazar si ya existe una
     * @throws SesionActivaException si hay otra sesión activa y no se pidió reemplazarla
     */
    public String iniciar(Long usuarioId, String plataforma, boolean reemplazar) {
        String clave = PREFIJO + usuarioId;
        String jti = UUID.randomUUID().toString();
        String valor = jti + SEPARADOR + plataforma;

        if (reemplazar) {
            redis.opsForValue().set(clave, valor, ttl);
            return jti;
        }
        // Atómico: dos inicios de sesión simultáneos no pueden quedar ambos activos.
        if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(clave, valor, ttl))) {
            return jti;
        }
        String actual = redis.opsForValue().get(clave);
        if (actual == null) { // expiró entre las dos llamadas
            redis.opsForValue().set(clave, valor, ttl);
            return jti;
        }
        throw new SesionActivaException(plataformaDe(actual));
    }

    /** Falla con 409 si hay otra sesión activa y no se pidió reemplazarla (sin abrir ni modificar nada). */
    public void verificarSinSesionActiva(Long usuarioId, boolean reemplazar) {
        if (reemplazar) {
            return;
        }
        String actual = redis.opsForValue().get(PREFIJO + usuarioId);
        if (actual != null) {
            throw new SesionActivaException(plataformaDe(actual));
        }
    }

    public boolean esVigente(Long usuarioId, String jti) {
        if (usuarioId == null || jti == null) {
            return false;
        }
        String actual = redis.opsForValue().get(PREFIJO + usuarioId);
        return actual != null && actual.startsWith(jti + SEPARADOR);
    }

    /** Cierra la sesión solo si el jti sigue siendo el activo (no borra la sesión de otra plataforma). */
    public void cerrar(Long usuarioId, String jti) {
        if (esVigente(usuarioId, jti)) {
            redis.delete(PREFIJO + usuarioId);
        }
    }

    /** Cierra cualquier sesión activa del usuario (por ejemplo, al restablecer su contraseña). */
    public void cerrarTodas(Long usuarioId) {
        redis.delete(PREFIJO + usuarioId);
    }

    private String plataformaDe(String valor) {
        int i = valor.indexOf(SEPARADOR);
        return i >= 0 ? valor.substring(i + 1) : "DESCONOCIDA";
    }
}
