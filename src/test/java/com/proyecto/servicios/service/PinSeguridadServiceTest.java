package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.Cuenta;
import com.proyecto.servicios.entity.Usuario;
import com.proyecto.servicios.exception.Codigo2faInvalidoException;
import com.proyecto.servicios.exception.CorreoNoDisponibleException;
import com.proyecto.servicios.exception.DemasiadosIntentosException;
import com.proyecto.servicios.exception.ValidacionNegocioException;
import com.proyecto.servicios.repositorys.CuentaRepository;
import com.proyecto.servicios.repositorys.UsuarioRepository;
import com.proyecto.servicios.security.CorreoOtpSender;
import com.proyecto.servicios.security.Otp2faService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PIN: cambio en pasos (PIN actual → código del correo → autorización) y PIN temporal por correo. */
class PinSeguridadServiceTest {

    private static final String CUENTA = "1234567890123456";
    private static final String CORREO = "mirza@correo.com";

    private final Map<String, String> store = new HashMap<>();
    private CuentaRepository cuentas;
    private PasswordEncoder encoder;
    private Otp2faService otp;
    private CorreoOtpSender correo;
    private PinSeguridadService servicio;
    private Cuenta cuenta;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        doAnswer(i -> store.put(i.getArgument(0), i.getArgument(1))).when(ops)
                .set(anyString(), anyString(), any(Duration.class));
        when(ops.get(anyString())).thenAnswer(i -> store.get(i.<String>getArgument(0)));
        when(ops.getAndDelete(anyString())).thenAnswer(i -> store.remove(i.<String>getArgument(0)));
        when(ops.increment(anyString())).thenAnswer(i -> {
            long n = Long.parseLong(store.getOrDefault(i.<String>getArgument(0), "0")) + 1;
            store.put(i.getArgument(0), String.valueOf(n));
            return n;
        });
        when(redis.delete(anyString())).thenAnswer(i -> store.remove(i.<String>getArgument(0)) != null);
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);

        cuenta = Cuenta.builder().numeroCuenta(CUENTA).pinAutorizacion("hashPin").build();
        cuentas = mock(CuentaRepository.class);
        when(cuentas.findByNumeroCuenta(CUENTA)).thenReturn(Optional.of(cuenta));

        Usuario usuario = Usuario.builder().id(7L).correo(CORREO).build();
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));

        encoder = mock(PasswordEncoder.class);
        when(encoder.matches(anyString(), eq("hashPin"))).thenAnswer(i -> "1111".equals(i.getArgument(0)));
        when(encoder.encode(anyString())).thenAnswer(i -> "hash-" + i.getArgument(0));

        otp = mock(Otp2faService.class);
        correo = mock(CorreoOtpSender.class);
        servicio = new PinSeguridadService(cuentas, usuarios, encoder, otp, correo, redis);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(CORREO, null, List.of()));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void iniciarCambioConPinCorrectoEnviaElCodigo() {
        when(otp.crearCambioPin(7L, CORREO, CUENTA))
                .thenReturn(new Otp2faService.Desafio("d1", "m***@correo.com", 300));

        Otp2faService.Desafio d = servicio.iniciarCambio(CUENTA, "1111");

        assertEquals("d1", d.desafioId());
    }

    @Test
    void iniciarCambioConPinIncorrectoNoEnviaCodigo() {
        assertThrows(ValidacionNegocioException.class, () -> servicio.iniciarCambio(CUENTA, "0000"));
        verify(otp, never()).crearCambioPin(any(), any(), any());
    }

    @Test
    void despuesDeCincoFallosElPinSeBloqueaTemporalmente() {
        for (int i = 0; i < 5; i++) {
            assertThrows(ValidacionNegocioException.class, () -> servicio.iniciarCambio(CUENTA, "0000"));
        }
        // Aun con el PIN correcto, la cuenta está bloqueada por intentos.
        assertThrows(DemasiadosIntentosException.class, () -> servicio.iniciarCambio(CUENTA, "1111"));
    }

    @Test
    void cuentaSinPinNoUsaElFlujoDeCambio() {
        cuenta.setPinAutorizacion(null);
        assertThrows(ValidacionNegocioException.class, () -> servicio.iniciarCambio(CUENTA, "1111"));
    }

    @Test
    void codigoCorrectoEntregaUnaAutorizacionDeUnSoloUso() {
        when(otp.verificar("d1", "123456")).thenReturn(new Otp2faService.Verificado(7L, "PIN:" + CUENTA, false));

        String autorizacion = servicio.confirmarCodigo(CUENTA, "d1", "123456");

        servicio.consumirAutorizacion(CUENTA, autorizacion);
        assertThrows(ValidacionNegocioException.class, () -> servicio.consumirAutorizacion(CUENTA, autorizacion));
    }

    @Test
    void laAutorizacionSoloValeParaLaCuentaVerificada() {
        when(otp.verificar("d1", "123456")).thenReturn(new Otp2faService.Verificado(7L, "PIN:" + CUENTA, false));
        String autorizacion = servicio.confirmarCodigo(CUENTA, "d1", "123456");

        assertThrows(ValidacionNegocioException.class,
                () -> servicio.consumirAutorizacion("9999999999999999", autorizacion));
    }

    @Test
    void unCodigoDeLoginORecuperacionNoCambiaElPin() {
        when(otp.verificar("d1", "123456")).thenReturn(new Otp2faService.Verificado(7L, "WEB", false));
        assertThrows(Codigo2faInvalidoException.class, () -> servicio.confirmarCodigo(CUENTA, "d1", "123456"));

        when(otp.verificar("d2", "123456")).thenReturn(new Otp2faService.Verificado(7L, "PIN:9999999999999999", false));
        assertThrows(Codigo2faInvalidoException.class, () -> servicio.confirmarCodigo(CUENTA, "d2", "123456"));
    }

    @Test
    void unCodigoDeOtroUsuarioNoSirve() {
        when(otp.verificar("d1", "123456")).thenReturn(new Otp2faService.Verificado(99L, "PIN:" + CUENTA, false));
        assertThrows(Codigo2faInvalidoException.class, () -> servicio.confirmarCodigo(CUENTA, "d1", "123456"));
    }

    @Test
    void olvideGeneraUnPinTemporalDeCuatroDigitosYLoEnviaPorCorreo() {
        servicio.olvide(CUENTA);

        ArgumentCaptor<String> pin = ArgumentCaptor.forClass(String.class);
        verify(correo).enviarPinTemporal(eq(CORREO), pin.capture());
        assertTrue(pin.getValue().matches("\\d{4}"));
        assertEquals("hash-" + pin.getValue(), cuenta.getPinAutorizacion()); // se guarda cifrado, nunca en claro
        verify(cuentas).save(cuenta);
    }

    @Test
    void siElCorreoFallaElPinNoCambia() {
        doThrow(new IllegalStateException("smtp")).when(correo).enviarPinTemporal(any(), any());

        assertThrows(CorreoNoDisponibleException.class, () -> servicio.olvide(CUENTA));

        assertEquals("hashPin", cuenta.getPinAutorizacion());
        verify(cuentas, never()).save(any());
    }

    @Test
    void olvideTieneLimitePorHora() {
        for (int i = 0; i < 3; i++) {
            servicio.olvide(CUENTA);
        }
        assertThrows(DemasiadosIntentosException.class, () -> servicio.olvide(CUENTA));
    }
}
