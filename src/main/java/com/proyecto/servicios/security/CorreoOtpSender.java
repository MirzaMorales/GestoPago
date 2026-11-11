package com.proyecto.servicios.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Envía el código de verificación (2FA) por correo electrónico. */
@Slf4j
@Component
public class CorreoOtpSender {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String remitente;
    private final boolean registrarCodigoEnLog;

    public CorreoOtpSender(ObjectProvider<JavaMailSender> mailSender,
                           // Remitente visible. Por defecto el usuario SMTP (Gmail); con proveedores como Brevo el usuario SMTP no es un
                           // correo, así que se define aparte con APP_2FA_REMITENTE (debe ser un remitente verificado).
                           @Value("${app.2fa.remitente:${spring.mail.username:}}") String remitente,
                           @Value("${app.2fa.log-codigo:false}") boolean registrarCodigoEnLog) {
        this.mailSender = mailSender;
        this.remitente = remitente;
        this.registrarCodigoEnLog = registrarCodigoEnLog;
    }

    /**
     * @throws IllegalStateException si no hay SMTP configurado
     * @throws org.springframework.mail.MailException si el servidor de correo rechaza el envío
     */
    public void enviar(String destino, String codigo, int minutosVigencia) {
        enviar(destino, codigo, minutosVigencia, false);
    }

    /** Código para restablecer la contraseña (mismo canal, otro texto). */
    public void enviarRecuperacion(String destino, String codigo, int minutosVigencia) {
        enviar(destino, codigo, minutosVigencia, true);
    }

    private void enviar(String destino, String codigo, int minutosVigencia, boolean recuperacion) {
        if (registrarCodigoEnLog) {
            // SOLO para desarrollo local sin SMTP (app.2fa.log-codigo=true). Nunca en producción.
            log.warn("[2FA-DEV] Código para {}: {}", destino, codigo);
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("El servidor de correo no está configurado");
        }
        SimpleMailMessage mensaje = new SimpleMailMessage();
        if (!remitente.isBlank()) {
            mensaje.setFrom(remitente);
        }
        mensaje.setTo(destino);
        if (recuperacion) {
            mensaje.setSubject("GestoPago: código para restablecer tu contraseña");
            mensaje.setText("Tu código para restablecer la contraseña es: " + codigo + "\n\n"
                    + "Vence en " + minutosVigencia + " minutos y solo puede usarse una vez.\n"
                    + "Si no lo solicitaste, ignora este mensaje: tu contraseña no cambiará.");
        } else {
            mensaje.setSubject("GestoPago: tu código de verificación");
            mensaje.setText("Tu código de verificación es: " + codigo + "\n\n"
                    + "Vence en " + minutosVigencia + " minutos y solo puede usarse una vez.\n"
                    + "Si no intentaste iniciar sesión, ignora este mensaje y cambia tu contraseña.");
        }
        sender.send(mensaje);
    }
}
