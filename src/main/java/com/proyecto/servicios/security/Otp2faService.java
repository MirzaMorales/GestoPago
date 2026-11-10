package com.proyecto.servicios.security;

import com.proyecto.servicios.exception.Codigo2faInvalidoException;
import com.proyecto.servicios.exception.CorreoNoDisponibleException;
import com.proyecto.servicios.exception.DemasiadosIntentosException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Segundo factor (OTP de 6 dígitos por correo) validado en el servidor.
 * El código nunca se guarda en claro: en Redis solo queda su hash, con vigencia corta,
 * número máximo de intentos y un solo uso.
 */
@Slf4j
@Service
public class Otp2faService {

    private static final String CLAVE_DESAFIO = "2fa:desafio:";
    private static final String CLAVE_INTENTOS = "2fa:intentos:";
    private static final String CLAVE_ENVIOS = "2fa:envios:";
    private static final String SEP = "|";

    public record Desafio(String desafioId, String correoEnmascarado, int expiraEnSegundos) { }

    public record Verificado(Long usuarioId, String plataforma, boolean cerrarSesionAnterior) { }

    private final StringRedisTemplate redis;
    private final CorreoOtpSender correo;
    private final SecureRandom random = new SecureRandom();
    private final int ttlSegundos;
    private final int maxIntentos;
    private final int maxEnvios;
    private final int ventanaEnviosMinutos;

    public Otp2faService(StringRedisTemplate redis,
                         CorreoOtpSender correo,
                         @Value("${app.2fa.ttl-segundos:300}") int ttlSegundos,
                         @Value("${app.2fa.max-intentos:5}") int maxIntentos,
                         @Value("${app.2fa.max-envios:5}") int maxEnvios,
                         @Value("${app.2fa.ventana-envios-minutos:15}") int ventanaEnviosMinutos) {
        this.redis = redis;
        this.correo = correo;
        this.ttlSegundos = ttlSegundos;
        this.maxIntentos = maxIntentos;
        this.maxEnvios = maxEnvios;
        this.ventanaEnviosMinutos = ventanaEnviosMinutos;
    }

    /** Genera un código, lo guarda (hash) y lo envía por correo. */
    public Desafio crear(Long usuarioId, String correoDestino, String plataforma, boolean cerrarSesionAnterior) {
        limitarEnvios(usuarioId);

        String codigo = String.format("%06d", random.nextInt(1_000_000));
        String desafioId = UUID.randomUUID().toString();
        String valor = usuarioId + SEP + plataforma + SEP + cerrarSesionAnterior + SEP + hash(desafioId, codigo);
        redis.opsForValue().set(CLAVE_DESAFIO + desafioId, valor, Duration.ofSeconds(ttlSegundos));

        try {
            correo.enviar(correoDestino, codigo, Math.max(1, ttlSegundos / 60));
        } catch (RuntimeException ex) {
            redis.delete(CLAVE_DESAFIO + desafioId);
            // Se registra el motivo real (autenticación SMTP, host, red...) sin incluir el código ni credenciales.
            log.error("No se pudo enviar el código 2FA ({}): {}", ex.getClass().getSimpleName(), causaRaiz(ex));
            throw new CorreoNoDisponibleException();
        }
        return new Desafio(desafioId, enmascarar(correoDestino), ttlSegundos);
    }

    /** Valida el código; si es correcto el desafío se consume (un solo uso). */
    public Verificado verificar(String desafioId, String codigo) {
        String valor = redis.opsForValue().get(CLAVE_DESAFIO + desafioId);
        if (valor == null) {
            throw new Codigo2faInvalidoException("El código expiró o no es válido. Inicie sesión nuevamente");
        }

        Long intentos = redis.opsForValue().increment(CLAVE_INTENTOS + desafioId);
        if (intentos != null && intentos == 1) {
            redis.expire(CLAVE_INTENTOS + desafioId, Duration.ofSeconds(ttlSegundos));
        }
        if (intentos != null && intentos > maxIntentos) {
            eliminar(desafioId);
            throw new DemasiadosIntentosException("Demasiados intentos. Inicie sesión nuevamente");
        }

        String[] partes = valor.split("\\|", 4);
        boolean coincide = partes.length == 4 && MessageDigest.isEqual(
                partes[3].getBytes(StandardCharsets.UTF_8),
                hash(desafioId, codigo == null ? "" : codigo.trim()).getBytes(StandardCharsets.UTF_8));
        if (!coincide) {
            throw new Codigo2faInvalidoException("El código es incorrecto");
        }

        eliminar(desafioId);
        return new Verificado(Long.valueOf(partes[0]), partes[1], Boolean.parseBoolean(partes[2]));
    }

    private static String causaRaiz(Throwable ex) {
        Throwable raiz = ex;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        String mensaje = raiz.getMessage() == null ? "" : raiz.getMessage().replaceAll("[\r\n\t ]+", " ");
        return raiz.getClass().getSimpleName() + ": " + (mensaje.length() > 300 ? mensaje.substring(0, 300) : mensaje);
    }

    private void limitarEnvios(Long usuarioId) {
        String clave = CLAVE_ENVIOS + usuarioId;
        Long envios = redis.opsForValue().increment(clave);
        if (envios != null && envios == 1) {
            redis.expire(clave, Duration.ofMinutes(ventanaEnviosMinutos));
        }
        if (envios != null && envios > maxEnvios) {
            throw new DemasiadosIntentosException(
                    "Se enviaron demasiados códigos. Intente de nuevo en unos minutos");
        }
    }

    private void eliminar(String desafioId) {
        redis.delete(CLAVE_DESAFIO + desafioId);
        redis.delete(CLAVE_INTENTOS + desafioId);
    }

    private static String hash(String desafioId, String codigo) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((desafioId + ":" + codigo).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String enmascarar(String correo) {
        int arroba = correo.indexOf('@');
        if (arroba <= 1) {
            return "***" + correo.substring(Math.max(arroba, 0));
        }
        return correo.charAt(0) + "***" + correo.substring(arroba);
    }
}
