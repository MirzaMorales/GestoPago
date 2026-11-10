package com.proyecto.servicios.security;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorreoOtpSenderTest {

    @SuppressWarnings("unchecked")
    private ObjectProvider<JavaMailSender> proveedor(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(sender);
        return p;
    }

    @Test
    void enviaElCodigoAlDestinatarioConElRemitenteConfigurado() {
        JavaMailSender sender = mock(JavaMailSender.class);

        new CorreoOtpSender(proveedor(sender), "verificado@gmail.com", false)
                .enviar("cliente@example.com", "123456", 5);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage m = captor.getValue();
        assertEquals("verificado@gmail.com", m.getFrom());
        assertArrayEquals(new String[]{"cliente@example.com"}, m.getTo());
        assertTrue(m.getText().contains("123456"));
    }

    @Test
    void sinSmtpConfiguradoFallaConUnMensajeClaro() {
        assertThrows(IllegalStateException.class,
                () -> new CorreoOtpSender(proveedor(null), "", false).enviar("a@b.com", "123456", 5));
    }

    @Test
    void elModoDeDesarrolloNoEnviaCorreo() {
        JavaMailSender sender = mock(JavaMailSender.class);

        new CorreoOtpSender(proveedor(sender), "", true).enviar("a@b.com", "123456", 5);

        verifyNoInteractions(sender);
    }
}
