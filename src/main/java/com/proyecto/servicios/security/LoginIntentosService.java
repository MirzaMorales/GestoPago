package com.proyecto.servicios.security;

import com.proyecto.servicios.exception.DemasiadosIntentosException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Protección contra fuerza bruta en el login: tras N credenciales incorrectas seguidas para un mismo correo
 * se bloquean nuevos intentos durante unos minutos. Se cuenta igual si el correo existe o no, para no revelar
 * qué cuentas están registradas.
 */
@Service
public class LoginIntentosService {

    private static final String PREFIJO = "login:fallos:";

    private final StringRedisTemplate redis;
    private final int maxFallos;
    private final Duration bloqueo;

    public LoginIntentosService(StringRedisTemplate redis,
                                @Value("${app.login.max-fallos:5}") int maxFallos,
                                @Value("${app.login.bloqueo-minutos:15}") int bloqueoMinutos) {
        this.redis = redis;
        this.maxFallos = maxFallos;
        this.bloqueo = Duration.ofMinutes(bloqueoMinutos);
    }

    /** Falla con 429 si el correo ya agotó sus intentos. */
    public void verificarNoBloqueado(String correo) {
        String valor = redis.opsForValue().get(PREFIJO + correo);
        if (valor != null && Long.parseLong(valor) >= maxFallos) {
            throw new DemasiadosIntentosException("Demasiados intentos fallidos. Intente de nuevo en "
                    + bloqueo.toMinutes() + " minutos");
        }
    }

    public void registrarFallo(String correo) {
        String clave = PREFIJO + correo;
        Long fallos = redis.opsForValue().increment(clave);
        // La ventana se abre en el primer fallo y se renueva completa al alcanzar el límite (inicia el bloqueo).
        if (fallos != null && (fallos == 1 || fallos == maxFallos)) {
            redis.expire(clave, bloqueo);
        }
    }

    public void limpiar(String correo) {
        redis.delete(PREFIJO + correo);
    }
}
