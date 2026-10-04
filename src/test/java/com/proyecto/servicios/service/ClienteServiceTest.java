package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.Cliente;
import com.proyecto.servicios.entity.Cuenta;
import com.proyecto.servicios.entity.Domicilio;
import com.proyecto.servicios.entity.Usuario;
import com.proyecto.servicios.exception.CurpDuplicadaException;
import com.proyecto.servicios.exception.RfcDuplicadoException;
import com.proyecto.servicios.exception.ValidacionNegocioException;
import com.proyecto.servicios.mapper.ClienteMapper;
import com.proyecto.servicios.model.request.DomicilioRequest;
import com.proyecto.servicios.model.request.RegistroClienteRequest;
import com.proyecto.servicios.model.response.ClienteResponse;
import com.proyecto.servicios.repositorys.ClienteRepository;
import com.proyecto.servicios.repositorys.CuentaRepository;
import com.proyecto.servicios.repositorys.DomicilioRepository;
import com.proyecto.servicios.repositorys.UsuarioRepository;
import com.proyecto.servicios.service.Impl.ClienteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private DomicilioRepository domicilioRepository;

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ClienteMapper clienteMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ClienteServiceImpl clienteService;

    private RegistroClienteRequest request;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        DomicilioRequest domReq = DomicilioRequest.builder()
                .calle("Av. Reforma")
                .numeroExterior("123")
                .colonia("Centro")
                .municipio("Cuauhtémoc")
                .estado("CDMX")
                .codigoPostal("06000")
                .pais("México")
                .build();

        request = RegistroClienteRequest.builder()
                .nombre("Juan")
                .apellidoPaterno("Pérez")
                .apellidoMaterno("Gómez")
                .fechaNacimiento(LocalDate.of(1995, 5, 20))
                .curp("PEGJ950520HDFRMN01")
                .rfc("PEGJ950520XXX")
                .sexo("MASCULINO")
                .nacionalidad("Mexicana")
                .estadoCivil("SOLTERO")
                .correo("juan.perez@example.com")
                .telefonoMovil("5512345678")
                .ocupacion("Ingeniero")
                .empresa("GestoPago")
                .ingresoMensual(new BigDecimal("25000.00"))
                .password("SecureP@ss123")
                .domicilio(domReq)
                .build();
    }

    @Test
    void testRegistrarClienteSuccess() {
        when(clienteRepository.existsByCurp(any())).thenReturn(false);
        when(clienteRepository.existsByRfc(any())).thenReturn(false);
        when(clienteRepository.existsByCorreo(any())).thenReturn(false);
        when(usuarioRepository.existsByCorreo(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed_password");

        Domicilio domMock = Domicilio.builder().id(1L).calle("Av. Reforma").build();
        when(domicilioRepository.save(any())).thenReturn(domMock);

        Cliente cliMock = Cliente.builder().id(10L).nombre("Juan").curp("PEGJ950520HDFRMN01").activo(true).build();
        when(clienteRepository.save(any())).thenReturn(cliMock);
        when(clienteRepository.findById(10L)).thenReturn(Optional.of(cliMock));

        ClienteResponse responseMock = ClienteResponse.builder().id(10L).nombre("Juan").activo(true).build();
        when(clienteMapper.toClienteResponse(any())).thenReturn(responseMock);

        ClienteResponse response = clienteService.registrarCliente(request);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("Juan", response.getNombre());

        verify(domicilioRepository, times(1)).save(any());
        verify(clienteRepository, times(1)).save(any());
        verify(cuentaRepository, times(1)).save(any());
        verify(usuarioRepository, times(1)).save(any());
        ArgumentCaptor<Cuenta> cuentaCaptor = ArgumentCaptor.forClass(Cuenta.class);
        verify(cuentaRepository).save(cuentaCaptor.capture());
        assertEquals(BigDecimal.ZERO, cuentaCaptor.getValue().getSaldo());
    }

    @Test
    void testRegistrarClienteMenorDeEdadThrowsException() {
        request.setFechaNacimiento(LocalDate.now().minusYears(17));

        ValidacionNegocioException exception = assertThrows(
                ValidacionNegocioException.class,
                () -> clienteService.registrarCliente(request)
        );

        assertTrue(exception.getMessage().contains("mayor de edad"));
        verify(clienteRepository, never()).save(any());
    }

    @Test
    void testRegistrarClienteCurpDuplicadaThrowsException() {
        when(clienteRepository.existsByCurp(any())).thenReturn(true);

        assertThrows(
                CurpDuplicadaException.class,
                () -> clienteService.registrarCliente(request)
        );

        verify(clienteRepository, never()).save(any());
    }

    @Test
    void testRegistrarClienteRfcDuplicadoThrowsException() {
        when(clienteRepository.existsByCurp(any())).thenReturn(false);
        when(clienteRepository.existsByRfc(any())).thenReturn(true);

        assertThrows(
                RfcDuplicadoException.class,
                () -> clienteService.registrarCliente(request)
        );

        verify(clienteRepository, never()).save(any());
    }

    @Test
    void testDarDeBajaLogicaClienteDisablesUserAndAccounts() {
        Usuario usuarioMock = Usuario.builder().id(5L).activo(true).build();
        Cuenta cuentaMock = Cuenta.builder().estatus(com.proyecto.servicios.entity.EstatusCuenta.ACTIVA).build();
        Cliente clienteMock = Cliente.builder()
                .id(10L)
                .activo(true)
                .usuario(usuarioMock)
                .cuentas(java.util.List.of(cuentaMock))
                .build();

        when(clienteRepository.findById(10L)).thenReturn(Optional.of(clienteMock));
        when(cuentaRepository.findByClienteId(10L)).thenReturn(java.util.List.of(cuentaMock));

        clienteService.darDeBajaLogicaCliente(10L);

        assertFalse(clienteMock.getActivo());
        assertFalse(usuarioMock.getActivo());
        assertEquals(com.proyecto.servicios.entity.EstatusCuenta.INACTIVA, cuentaMock.getEstatus());
        verify(clienteRepository, times(1)).save(clienteMock);
        verify(usuarioRepository).save(usuarioMock);
    }
}
