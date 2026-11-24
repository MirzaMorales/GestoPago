package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.Cuenta;
import com.proyecto.servicios.entity.Usuario;
import com.proyecto.servicios.exception.Codigo2faInvalidoException;
import com.proyecto.servicios.exception.CorreoNoDisponibleException;
import com.proyecto.servicios.exception.CuentaNoEncontradaException;
import com.proyecto.servicios.exception.DemasiadosIntentosException;
import com.proyecto.servicios.exception.ValidacionNegocioException;
import com.proyecto.servicios.repositorys.CuentaRepository;
import com.proyecto.servicios.repositorys.UsuarioRepository;
import com.proyecto.servicios.security.CorreoOtpSender;
import com.proyecto.servicios.security.Otp2faService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

/**
 * Seguridad del PIN de compras:
 * <ul>
 *   <li>Cambio: PIN actual → código por correo (segundo factor) → autorización de un solo uso → PIN nuevo.</li>
 *   <li>"Olvidé mi PIN": se genera un PIN temporal, se guarda cifrado y se envía por correo al titular.</li>
 * </ul>
 */
@Slf4j
@Service
public class PinSeguridadService {

    private static final String CLAVE_FALLOS = "pin:fallos:";
    private static final String CLAVE_AUTORIZACION = "pin:autorizacion:";
    private static final String CLAVE_OLVIDE = "pin:olvide:";
    private static final int MAX_FALLOS = 5;
    private static final int MAX_OLVIDE_POR_HORA = 3;
    private static final Duration VIGENCIA_AUTORIZACION = Duration.ofMinutes(5);

    private final CuentaRepository cuentas;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final Otp2faService otp;
    private final CorreoOtpSender correo;
    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public PinSeguridadService(CuentaRepository cuentas, UsuarioRepository usuarios, PasswordEncoder passwordEncoder,
                               Otp2faService otp, CorreoOtpSender correo, StringRedisTemplate redis) {
        this.cuentas = cuentas;
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.otp = otp;
        this.correo = correo;
        this.redis = redis;
    }

    /** Paso 1 del cambio: valida el PIN actual y envía el código por correo. */
    public Otp2faService.Desafio iniciarCambio(String numeroCuenta, String pinActual) {
        Cuenta cuenta = cuenta(numeroCuenta);
        if (cuenta.getPinAutorizacion() == null) {
            throw new ValidacionNegocioException("Esta cuenta aún no tiene PIN: configúralo directamente");
        }
        verificarPinActual(cuenta, pinActual);
        Usuario usuario = usuarioActual();
        return otp.crearCambioPin(usuario.getId(), usuario.getCorreo(), cuenta.getNumeroCuenta());
    }

    /** Paso 2: valida el código; devuelve una autorización de un solo uso para fijar el PIN nuevo. */
    public String confirmarCodigo(String numeroCuenta, String desafioId, String codigo) {
        Usuario usuario = usuarioActual();
        Otp2faService.Verificado v = otp.verificar(desafioId, codigo);
        boolean valido = v.usuarioId().equals(usuario.getId())
                && (Otp2faService.PROPOSITO_PIN + numeroCuenta).equals(v.plataforma());
        if (!valido) {
            throw new Codigo2faInvalidoException("El código expiró o no es válido. Solicita uno nuevo");
        }
        String autorizacionId = UUID.randomUUID().toString();
        redis.opsForValue().set(CLAVE_AUTORIZACION + autorizacionId, usuario.getId() + "|" + numeroCuenta,
                VIGENCIA_AUTORIZACION);
        return autorizacionId;
    }

    /** Consume la autorización (un solo uso): solo vale para el mismo usuario y la misma cuenta. */
    public void consumirAutorizacion(String numeroCuenta, String autorizacionId) {
        String esperado = usuarioActual().getId() + "|" + numeroCuenta;
        String guardado = autorizacionId == null ? null : redis.opsForValue().getAndDelete(CLAVE_AUTORIZACION + autorizacionId);
        if (!esperado.equals(guardado)) {
            throw new ValidacionNegocioException("La verificación expiró. Vuelve a empezar el cambio de PIN");
        }
    }

    /** Valida el PIN actual con límite de intentos fallidos por cuenta. */
    public void verificarPinActual(Cuenta cuenta, String pinActual) {
        String clave = CLAVE_FALLOS + cuenta.getNumeroCuenta();
        String previos = redis.opsForValue().get(clave);
        if (previos != null && Integer.parseInt(previos) >= MAX_FALLOS) {
            throw new DemasiadosIntentosException("Demasiados intentos con el PIN. Intenta de nuevo en unos minutos");
        }
        if (pinActual == null || !passwordEncoder.matches(pinActual, cuenta.getPinAutorizacion())) {
            Long fallos = redis.opsForValue().increment(clave);
            if (fallos != null && fallos == 1) {
                redis.expire(clave, Duration.ofMinutes(15));
            }
            throw new ValidacionNegocioException("El PIN actual es incorrecto");
        }
        redis.delete(clave);
    }

    /** "Olvidé mi PIN": genera un PIN temporal y lo envía al correo del titular. */
    @Transactional
    public void olvide(String numeroCuenta) {
        Cuenta cuenta = cuenta(numeroCuenta);
        Usuario usuario = usuarioActual();
        String claveLimite = CLAVE_OLVIDE + usuario.getId();
        Long solicitudes = redis.opsForValue().increment(claveLimite);
        if (solicitudes != null && solicitudes == 1) {
            redis.expire(claveLimite, Duration.ofHours(1));
        }
        if (solicitudes != null && solicitudes > MAX_OLVIDE_POR_HORA) {
            throw new DemasiadosIntentosException("Solicitaste demasiados PIN temporales. Intenta de nuevo más tarde");
        }

        String temporal = String.format("%04d", random.nextInt(10_000));
        try {
            correo.enviarPinTemporal(usuario.getCorreo(), temporal);
        } catch (RuntimeException ex) {
            log.error("No se pudo enviar el PIN temporal ({})", ex.getClass().getSimpleName());
            throw new CorreoNoDisponibleException();
        }
        cuenta.setPinAutorizacion(passwordEncoder.encode(temporal));
        cuentas.save(cuenta);
        redis.delete(CLAVE_FALLOS + cuenta.getNumeroCuenta());
        log.info("PIN temporal enviado para el usuario {}", usuario.getId());
    }

    private Cuenta cuenta(String numeroCuenta) {
        return cuentas.findByNumeroCuenta(numeroCuenta.trim())
                .orElseThrow(() -> new CuentaNoEncontradaException("No se encontró la cuenta bancaria: " + numeroCuenta));
    }

    private Usuario usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof String correoUsuario)) {
            throw new ValidacionNegocioException("No se pudo identificar al usuario");
        }
        return usuarios.findByCorreo(correoUsuario)
                .orElseThrow(() -> new ValidacionNegocioException("No se pudo identificar al usuario"));
    }
}
