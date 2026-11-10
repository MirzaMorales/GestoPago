package com.proyecto.servicios.service;

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

class LimiteConsultasProveedorTest {

    private final Map<String, String> store = new HashMap<>();
    private StringRedisTemplate redis;
    private LimiteConsultasProveedor limite;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenAnswer(i -> {
            long n = Long.parseLong(store.getOrDefault(i.<String>getArgument(0), "0")) + 1;
            store.put(i.getArgument(0), String.valueOf(n));
            return n;
        });
        limite = new LimiteConsultasProveedor(redis, 2, "America/Mexico_City");
    }

    @Test
    void permiteSoloElCupoDiarioYDespuesBloquea() {
        assertTrue(limite.reservar());
        assertTrue(limite.reservar());
        assertFalse(limite.reservar());
        assertFalse(limite.reservar());
    }

    @Test
    void elContadorExpiraSoloAlCrearse() {
        limite.reservar();
        limite.reservar();
        verify(redis, times(1)).expire(anyString(), eq(Duration.ofHours(30)));
    }

    @Test
    void siRedisFallaNoSeLlamaAlProveedor() {
        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis caído"));
        assertFalse(limite.reservar());
    }
}
