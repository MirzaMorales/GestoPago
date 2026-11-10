package com.proyecto.servicios.security;

import com.proyecto.servicios.exception.Codigo2faInvalidoException;
import com.proyecto.servicios.exception.CorreoNoDisponibleException;
import com.proyecto.servicios.exception.DemasiadosIntentosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Redis simulado con un mapa en memoria: se prueba la lógica de OTP sin infraestructura. */
class Otp2faServiceTest {

    private final Map<String, String> store = new HashMap<>();
    private CorreoOtpSender correo;
    private Otp2faService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        doAnswer(i -> store.put(i.getArgument(0), i.getArgument(1))).when(ops)
                .set(anyString(), anyString(), any(Duration.class));
        when(ops.get(anyString())).thenAnswer(i -> store.get(i.<String>getArgument(0)));
        when(ops.increment(anyString())).thenAnswer(i -> {
            long n = Long.parseLong(store.getOrDefault(i.<String>getArgument(0), "0")) + 1;
            store.put(i.getArgument(0), String.valueOf(n));
            return n;
        });
        when(redis.delete(anyString())).thenAnswer(i -> store.remove(i.<String>getArgument(0)) != null);
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);

        correo = mock(CorreoOtpSender.class);
        service = new Otp2faService(redis, correo, 300, 3, 2, 15);
    }

    private String codigoEnviado() {
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(correo, atLeastOnce()).enviar(anyString(), codigo.capture(), anyInt());
        return codigo.getValue();
    }

    @Test
    void codigoCorrectoCompletaElDesafioUnaSolaVez() {
        Otp2faService.Desafio d = service.crear(7L, "usuario@gmail.com", "MOVIL", true);

        assertEquals("u***@gmail.com", d.correoEnmascarado());
        String codigo = codigoEnviado();
        assertTrue(codigo.matches("\\d{6}"));
        assertTrue(store.values().stream().noneMatch(v -> v.contains(codigo) && !v.contains("|")),
                "el código no debe guardarse en claro");

        Otp2faService.Verificado v = service.verificar(d.desafioId(), codigo);
        assertEquals(7L, v.usuarioId());
        assertEquals("MOVIL", v.plataforma());
        assertTrue(v.cerrarSesionAnterior());

        assertThrows(Codigo2faInvalidoException.class, () -> service.verificar(d.desafioId(), codigo));
    }

    @Test
    void codigoIncorrectoSeRechazaYTrasElMaximoDeIntentosSeBloquea() {
        Otp2faService.Desafio d = service.crear(7L, "usuario@gmail.com", "WEB", false);
        String codigo = codigoEnviado();
        String malo = codigo.equals("000000") ? "111111" : "000000";

        assertThrows(Codigo2faInvalidoException.class, () -> service.verificar(d.desafioId(), malo));
        assertThrows(Codigo2faInvalidoException.class, () -> service.verificar(d.desafioId(), malo));
        assertThrows(Codigo2faInvalidoException.class, () -> service.verificar(d.desafioId(), malo));
        assertThrows(DemasiadosIntentosException.class, () -> service.verificar(d.desafioId(), codigo));
        // tras el bloqueo ni el código correcto sirve
        assertThrows(Codigo2faInvalidoException.class, () -> service.verificar(d.desafioId(), codigo));
    }

    @Test
    void desafioInexistenteOExpiradoSeRechaza() {
        assertThrows(Codigo2faInvalidoException.class, () -> service.verificar("no-existe", "123456"));
    }

    @Test
    void limitaLosEnviosPorUsuario() {
        service.crear(7L, "usuario@gmail.com", "WEB", false);
        service.crear(7L, "usuario@gmail.com", "WEB", false);
        assertThrows(DemasiadosIntentosException.class,
                () -> service.crear(7L, "usuario@gmail.com", "WEB", false));
    }

    @Test
    void siFallaElCorreoNoQuedaDesafioYSeAvisa() {
        doThrow(new IllegalStateException("smtp caído")).when(correo).enviar(anyString(), anyString(), anyInt());

        assertThrows(CorreoNoDisponibleException.class,
                () -> service.crear(7L, "usuario@gmail.com", "WEB", false));
        assertTrue(store.keySet().stream().noneMatch(k -> k.startsWith("2fa:desafio:")));
    }
}
