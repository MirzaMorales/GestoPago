package com.proyecto.servicios.security;

import com.proyecto.servicios.exception.SesionActivaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SesionServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private SesionService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        service = new SesionService(redis, 3_600_000L);
    }

    @Test
    void abreSesionCuandoNoHayOtraActiva() {
        when(ops.setIfAbsent(eq("sesion:usuario:5"), anyString(), eq(Duration.ofHours(1)))).thenReturn(true);

        String jti = service.iniciar(5L, "MOVIL", false);

        assertNotNull(jti);
        verify(ops).setIfAbsent(eq("sesion:usuario:5"), eq(jti + "|MOVIL"), any(Duration.class));
    }

    @Test
    void rechazaConPlataformaActivaSiYaHaySesion() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(ops.get("sesion:usuario:5")).thenReturn("otro-jti|MOVIL");

        SesionActivaException ex = assertThrows(SesionActivaException.class,
                () -> service.iniciar(5L, "WEB", false));

        assertEquals("MOVIL", ex.getPlataformaActiva());
    }

    @Test
    void reemplazaLaSesionAnteriorCuandoSeSolicita() {
        String jti = service.iniciar(5L, "WEB", true);

        verify(ops).set("sesion:usuario:5", jti + "|WEB", Duration.ofHours(1));
        verify(ops, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void soloLaSesionActivaEsVigente() {
        when(ops.get("sesion:usuario:5")).thenReturn("jti-nuevo|WEB");

        assertTrue(service.esVigente(5L, "jti-nuevo"));
        assertFalse(service.esVigente(5L, "jti-viejo"));
        assertFalse(service.esVigente(5L, null));
        assertFalse(service.esVigente(null, "jti-nuevo"));
    }

    @Test
    void cerrarNoBorraLaSesionDeOtraPlataforma() {
        when(ops.get("sesion:usuario:5")).thenReturn("jti-nuevo|WEB");

        service.cerrar(5L, "jti-viejo");
        verify(redis, never()).delete(anyString());

        service.cerrar(5L, "jti-nuevo");
        verify(redis).delete("sesion:usuario:5");
    }
}
