package com.proyecto.servicios.security;

import com.proyecto.servicios.entity.Cliente;
import com.proyecto.servicios.entity.Cuenta;
import com.proyecto.servicios.entity.Usuario;
import com.proyecto.servicios.repositorys.CuentaRepository;
import com.proyecto.servicios.repositorys.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class AccesosServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private CuentaRepository cuentaRepository;
    @InjectMocks
    private AccesosService accesos;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ana@example.com", null, Collections.emptyList()));
        Cliente cliente = new Cliente();
        cliente.setId(10L);
        Usuario usuario = Usuario.builder().id(5L).cliente(cliente).build();
        when(usuarioRepository.findByCorreo("ana@example.com")).thenReturn(Optional.of(usuario));
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    @Test
    void permiteSoloSuPropioCliente() {
        assertTrue(accesos.esClienteId(10L));
        assertFalse(accesos.esClienteId(11L));
    }

    @Test
    void permiteSoloSuPropioUsuario() {
        assertTrue(accesos.esUsuarioId(5L));
        assertFalse(accesos.esUsuarioId(6L));
    }

    @Test
    void permiteSoloCuentasPropias() {
        Cliente otro = new Cliente();
        otro.setId(99L);
        Cliente propio = new Cliente();
        propio.setId(10L);
        Cuenta cuentaPropia = Cuenta.builder().cliente(propio).build();
        Cuenta cuentaAjena = Cuenta.builder().cliente(otro).build();
        when(cuentaRepository.findByNumeroCuenta("1111111111111111")).thenReturn(Optional.of(cuentaPropia));
        when(cuentaRepository.findByNumeroCuenta("2222222222222222")).thenReturn(Optional.of(cuentaAjena));

        assertTrue(accesos.esDuenoDeCuenta("1111111111111111"));
        assertFalse(accesos.esDuenoDeCuenta("2222222222222222"));
        assertFalse(accesos.esDuenoDeCuenta("3333333333333333"));
    }
}
