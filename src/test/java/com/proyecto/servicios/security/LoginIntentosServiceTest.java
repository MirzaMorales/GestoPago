package com.proyecto.servicios.security;

import com.proyecto.servicios.exception.DemasiadosIntentosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginIntentosServiceTest {

    private final Map<String, String> store = new HashMap<>();
    private StringRedisTemplate redis;
    private LoginIntentosService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(i -> store.get(i.<String>getArgument(0)));
        when(ops.increment(anyString())).thenAnswer(i -> {
            long n = Long.parseLong(store.getOrDefault(i.<String>getArgument(0), "0")) + 1;
            store.put(i.getArgument(0), String.valueOf(n));
            return n;
        });
        when(redis.delete(anyString())).thenAnswer(i -> store.remove(i.<String>getArgument(0)) != null);
        service = new LoginIntentosService(redis, 3, 15);
    }

    @Test
    void sePermiteIntentarMientrasNoSeAlcanceElLimite() {
        service.registrarFallo("a@x.com");
        service.registrarFallo("a@x.com");
        assertDoesNotThrow(() -> service.verificarNoBloqueado("a@x.com"));
    }

    @Test
    void alAlcanzarElLimiteSeBloqueaYSeIniciaLaVentanaDeBloqueo() {
        service.registrarFallo("a@x.com");
        service.registrarFallo("a@x.com");
        service.registrarFallo("a@x.com");

        assertThrows(DemasiadosIntentosException.class, () -> service.verificarNoBloqueado("a@x.com"));
        verify(redis, times(2)).expire("login:fallos:a@x.com", Duration.ofMinutes(15)); // primer fallo y al llegar al límite
    }

    @Test
    void unLoginCorrectoReiniciaElContador() {
        service.registrarFallo("a@x.com");
        service.registrarFallo("a@x.com");
        service.limpiar("a@x.com");
        service.registrarFallo("a@x.com");

        assertDoesNotThrow(() -> service.verificarNoBloqueado("a@x.com"));
    }

    @Test
    void elBloqueoEsPorCorreo() {
        service.registrarFallo("a@x.com");
        service.registrarFallo("a@x.com");
        service.registrarFallo("a@x.com");

        assertDoesNotThrow(() -> service.verificarNoBloqueado("otro@x.com"));
    }
}
