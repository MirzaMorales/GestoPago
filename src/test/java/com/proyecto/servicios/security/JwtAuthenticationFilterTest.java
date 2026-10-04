package com.proyecto.servicios.security;

import com.proyecto.servicios.repositorys.UsuarioRepository;
import com.proyecto.servicios.entity.Usuario;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    @Test
    void acceptsTokenOnlyForAnActiveUserWithAnActiveClient() throws Exception {
        when(tokenProvider.validateToken("jwt")).thenReturn(true);
        when(tokenProvider.getCorreoFromToken("jwt")).thenReturn("cliente@example.com");
        when(usuarioRepository.findActiveByCorreo("cliente@example.com")).thenReturn(Optional.of(new Usuario()));
        MockHttpServletRequest request = authorizedRequest();

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertEquals("cliente@example.com", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void rejectsTokenWhenUserOrClientIsInactive() throws Exception {
        when(tokenProvider.validateToken("jwt")).thenReturn(true);
        when(tokenProvider.getCorreoFromToken("jwt")).thenReturn("cliente@example.com");
        when(usuarioRepository.findActiveByCorreo("cliente@example.com")).thenReturn(Optional.empty());

        filter.doFilter(authorizedRequest(), new MockHttpServletResponse(), mock(FilterChain.class));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private MockHttpServletRequest authorizedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer jwt");
        return request;
    }
}
