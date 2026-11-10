package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.Rol;
import com.proyecto.servicios.entity.Usuario;
import com.proyecto.servicios.exception.CredencialesInvalidasException;
import com.proyecto.servicios.exception.DemasiadosIntentosException;
import com.proyecto.servicios.exception.SesionActivaException;
import com.proyecto.servicios.model.request.LoginRequest;
import com.proyecto.servicios.model.request.Verificacion2faRequest;
import com.proyecto.servicios.model.response.JwtAuthResponse;
import com.proyecto.servicios.model.response.Login2faResponse;
import com.proyecto.servicios.repositorys.UsuarioRepository;
import com.proyecto.servicios.security.JwtTokenProvider;
import com.proyecto.servicios.security.LoginIntentosService;
import com.proyecto.servicios.security.Otp2faService;
import com.proyecto.servicios.security.SesionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceImplTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private SesionService sesionService;
    @Mock private Otp2faService otp2faService;
    @Mock private LoginIntentosService loginIntentos;
    @InjectMocks private AuthServiceImpl service;

    private Usuario admin;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        admin = Usuario.builder().id(1L).correo("admin@example.com").password("hash")
                .rol(Rol.ADMIN).activo(true).build();
    }

    private LoginRequest login(String plataforma, Boolean cerrar) {
        return LoginRequest.builder().correo(" Admin@Example.com ").password("Clave#123")
                .plataforma(plataforma).cerrarSesionAnterior(cerrar).build();
    }

    @Test
    void loginValidoEnviaElCodigoYNoEntregaToken() {
        when(usuarioRepository.findByCorreo("admin@example.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Clave#123", "hash")).thenReturn(true);
        when(otp2faService.crear(1L, "admin@example.com", "MOVIL", false))
                .thenReturn(new Otp2faService.Desafio("d-1", "a***@example.com", 300));

        Login2faResponse r = service.login(login("MOVIL", null));

        assertTrue(r.isRequiere2fa());
        assertEquals("d-1", r.getDesafioId());
        verify(jwtTokenProvider, never()).generateToken(any(), any(), any(), any());
        verify(sesionService, never()).iniciar(any(), any(), anyBoolean());
    }

    @Test
    void passwordIncorrectaNoEnviaCodigo() {
        when(usuarioRepository.findByCorreo("admin@example.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThrows(CredencialesInvalidasException.class, () -> service.login(login("WEB", null)));
        verifyNoInteractions(otp2faService);
        verify(loginIntentos).registrarFallo("admin@example.com");
    }

    @Test
    void correoInexistenteTambienCuentaComoFallo() {
        when(usuarioRepository.findByCorreo("admin@example.com")).thenReturn(Optional.empty());

        assertThrows(CredencialesInvalidasException.class, () -> service.login(login("WEB", null)));
        verify(loginIntentos).registrarFallo("admin@example.com");
    }

    @Test
    void correoBloqueadoNoLlegaANiSiquieraAConsultarElUsuario() {
        doThrow(new DemasiadosIntentosException("bloqueado")).when(loginIntentos).verificarNoBloqueado("admin@example.com");

        assertThrows(DemasiadosIntentosException.class, () -> service.login(login("WEB", null)));
        verifyNoInteractions(usuarioRepository, otp2faService);
    }

    @Test
    void loginCorrectoLimpiaLosFallos() {
        when(usuarioRepository.findByCorreo("admin@example.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Clave#123", "hash")).thenReturn(true);
        when(otp2faService.crear(any(), any(), any(), anyBoolean()))
                .thenReturn(new Otp2faService.Desafio("d-1", "a***@example.com", 300));

        service.login(login("WEB", null));

        verify(loginIntentos).limpiar("admin@example.com");
    }

    @Test
    void siHaySesionActivaSeRechazaAntesDeEnviarElCodigo() {
        when(usuarioRepository.findByCorreo("admin@example.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        doThrow(new SesionActivaException("MOVIL")).when(sesionService).verificarSinSesionActiva(1L, false);

        assertThrows(SesionActivaException.class, () -> service.login(login("WEB", null)));
        verifyNoInteractions(otp2faService);
    }

    @Test
    void verificar2faCorrectoAbreLaSesionYEntregaElToken() {
        when(otp2faService.verificar("d-1", "123456"))
                .thenReturn(new Otp2faService.Verificado(1L, "WEB", true));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(sesionService.iniciar(1L, "WEB", true)).thenReturn("jti-1");
        when(jwtTokenProvider.generateToken("admin@example.com", 1L, null, "jti-1")).thenReturn("jwt");

        JwtAuthResponse r = service.verificar2fa(
                Verificacion2faRequest.builder().desafioId("d-1").codigo("123456").build());

        assertEquals("jwt", r.getAccessToken());
        assertEquals("ADMIN", r.getRol());
    }
}
