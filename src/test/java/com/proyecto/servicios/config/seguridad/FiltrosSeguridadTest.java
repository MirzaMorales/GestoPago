package com.proyecto.servicios.config.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.proyecto.servicios.exception.ApiErrorResponseWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FiltrosSeguridadTest {

    private final ApiErrorResponseWriter errores =
            new ApiErrorResponseWriter(new ObjectMapper().registerModule(new JavaTimeModule()));

    // ------------------------------------------------------------------ tamaño del cuerpo

    @Test
    void cuerpoDeclaradoMayorAlLimiteResponde413SinLlegarAlControlador() throws Exception {
        var filtro = new LimiteTamanoSolicitudFilter(1024, errores);
        var req = new MockHttpServletRequest("POST", "/auth/login");
        req.setContent(new byte[2048]);
        var res = new MockHttpServletResponse();
        var cadena = new MockFilterChain();

        filtro.doFilter(req, res, cadena);

        assertEquals(413, res.getStatus());
        assertTrue(res.getContentAsString().contains("tamaño permitido"));
        assertNull(cadena.getRequest(), "la petición no debe seguir");
    }

    @Test
    void cuerpoSinContentLengthSeCortaAlPasarElLimite() throws Exception {
        var filtro = new LimiteTamanoSolicitudFilter(100, errores);
        var req = new MockHttpServletRequest("POST", "/clientes") {
            @Override
            public long getContentLengthLong() {
                return -1; // envío chunked
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        req.setContent(new byte[500]);
        var cadena = new MockFilterChain();

        filtro.doFilter(req, new MockHttpServletResponse(), cadena);

        InputStream cuerpo = cadena.getRequest().getInputStream();
        assertThrows(IOException.class, () -> cuerpo.readAllBytes());
    }

    @Test
    void cuerpoNormalPasa() throws Exception {
        var filtro = new LimiteTamanoSolicitudFilter(1024, errores);
        var req = new MockHttpServletRequest("POST", "/auth/login");
        req.setContent("{\"correo\":\"a@b.com\"}".getBytes());
        var cadena = new MockFilterChain();

        filtro.doFilter(req, new MockHttpServletResponse(), cadena);

        assertNotNull(cadena.getRequest());
    }

    // ------------------------------------------------------------------ límite por IP

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valores;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void redisSimulado() {
        redis = mock(StringRedisTemplate.class);
        valores = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valores);
    }

    private LimitePeticionesFilter limitador() {
        return new LimitePeticionesFilter(redis, errores, 10, 15, 10, 120, 300);
    }

    @Test
    void loginPasaDentroDelLimiteYSeBloqueaAlExcederlo() throws Exception {
        when(valores.increment(contains("limite:login:"))).thenReturn(10L, 11L);

        var permitido = new MockHttpServletResponse();
        var cadena1 = new MockFilterChain();
        limitador().doFilter(new MockHttpServletRequest("POST", "/auth/login"), permitido, cadena1);
        assertNotNull(cadena1.getRequest());

        var bloqueado = new MockHttpServletResponse();
        var cadena2 = new MockFilterChain();
        limitador().doFilter(new MockHttpServletRequest("POST", "/auth/login"), bloqueado, cadena2);
        assertEquals(429, bloqueado.getStatus());
        assertNotNull(bloqueado.getHeader("Retry-After"));
        assertNull(cadena2.getRequest());
    }

    @Test
    void registroUsaSuPropioLimiteYBuscarClientesNo() throws Exception {
        when(valores.increment(anyString())).thenReturn(1L);
        when(valores.increment(contains("limite:registro:"))).thenReturn(11L);

        var registro = new MockHttpServletResponse();
        limitador().doFilter(new MockHttpServletRequest("POST", "/clientes"), registro, new MockFilterChain());
        assertEquals(429, registro.getStatus());

        var buscar = new MockHttpServletResponse();
        var cadena = new MockFilterChain();
        limitador().doFilter(new MockHttpServletRequest("POST", "/clientes/buscar"), buscar, cadena);
        assertNotNull(cadena.getRequest(), "/clientes/buscar va con el límite general");
    }

    @Test
    void siRedisFallaNoSeBloqueaElServicio() throws Exception {
        when(valores.increment(anyString())).thenThrow(new IllegalStateException("redis caído"));
        var cadena = new MockFilterChain();

        limitador().doFilter(new MockHttpServletRequest("POST", "/auth/login"), new MockHttpServletResponse(), cadena);

        assertNotNull(cadena.getRequest());
    }

    @Test
    void laIpSeTomaDeCloudflareYSeIgnoranValoresBasura() {
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "1.1.1.1");
        req.addHeader("CF-Connecting-IP", "200.10.20.30");
        assertEquals("200.10.20.30", LimitePeticionesFilter.ipCliente(req));

        var basura = new MockHttpServletRequest();
        basura.setRemoteAddr("10.0.0.2");
        basura.addHeader("X-Forwarded-For", "<script>, 1.2.3.4");
        assertEquals("10.0.0.2", LimitePeticionesFilter.ipCliente(basura));
    }

    // ------------------------------------------------------------------ errores de Tomcat

    @Test
    void erroresDeTomcatNoRevelanDetalles() {
        assertEquals("La solicitud no es válida", ErroresTomcatJsonValve.mensaje(400));
        assertEquals("Ocurrió un error interno al procesar la solicitud", ErroresTomcatJsonValve.mensaje(503));
    }
}
