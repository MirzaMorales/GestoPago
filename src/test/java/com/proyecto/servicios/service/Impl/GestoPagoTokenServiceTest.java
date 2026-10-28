package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoAuthClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.mapper.GestoPagoTokenMapper;
import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoTokenRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** El proveedor exige un token por día: no se debe pedir uno nuevo en cada ciclo del scheduler ni al reiniciar. */
class GestoPagoTokenServiceTest {

    @Mock
    private GestoPagoAuthClient authClient;

    @Mock
    private GestoPagoTokenRepository tokenRepository;

    @Mock
    private GestoPagoTokenMapper tokenMapper;

    private GestoPagoTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GestoPagoTokenServiceImpl(authClient, tokenRepository, tokenMapper);
        ReflectionTestUtils.setField(service, "idDistribuidor", 83);
        ReflectionTestUtils.setField(service, "codigoDispositivo", "DEV");
        ReflectionTestUtils.setField(service, "password", "secreto-de-prueba");
        ReflectionTestUtils.setField(service, "edadMaximaHoras", 23L);
    }

    private GestoPagoToken tokenDeHace(long horas) {
        GestoPagoToken t = new GestoPagoToken();
        t.setToken("tok");
        t.setFechaCreacion(LocalDateTime.now().minusHours(horas));
        t.setFechaActualizacion(LocalDateTime.now().minusHours(horas));
        return t;
    }

    private GestoPagoAuthResponse respuesta() {
        GestoPagoAuthResponse r = new GestoPagoAuthResponse();
        r.setToken("nuevo");
        return r;
    }

    @Test
    void renovarToken_conTokenVigente_noPideUnoNuevo() {
        when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(83, "DEV")).thenReturn(Optional.of(tokenDeHace(2)));

        service.renovarToken();

        verifyNoInteractions(authClient);
    }

    @Test
    void renovarToken_conTokenDeMasDe23Horas_pideUnoNuevo() {
        GestoPagoToken viejo = tokenDeHace(24);
        when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(83, "DEV")).thenReturn(Optional.of(viejo));
        when(authClient.authenticate(83, "DEV", "secreto-de-prueba")).thenReturn(respuesta());

        service.renovarToken();

        verify(authClient, times(1)).authenticate(83, "DEV", "secreto-de-prueba");
        verify(tokenRepository).save(viejo);
    }

    @Test
    void renovarToken_sinTokenGuardado_pideUnoNuevo() {
        when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(83, "DEV")).thenReturn(Optional.empty());
        when(authClient.authenticate(anyInt(), anyString(), anyString())).thenReturn(respuesta());
        when(tokenMapper.toEntity(any())).thenReturn(new GestoPagoToken());

        service.renovarToken();

        verify(authClient).authenticate(83, "DEV", "secreto-de-prueba");
    }

    @Test
    void renovarTokenForzado_ignoraLaEdadDelToken() {
        when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(83, "DEV")).thenReturn(Optional.of(tokenDeHace(1)));
        when(authClient.authenticate(83, "DEV", "secreto-de-prueba")).thenReturn(respuesta());

        service.renovarTokenForzado();

        verify(authClient).authenticate(83, "DEV", "secreto-de-prueba");
    }

    @Test
    void renovarToken_siElProveedorFalla_noPropagaLaExcepcion() {
        when(tokenRepository.findByIdDistribuidorAndCodigoDispositivo(83, "DEV")).thenReturn(Optional.empty());
        when(authClient.authenticate(anyInt(), anyString(), anyString())).thenThrow(mock(FeignException.class));

        assertDoesNotThrow(() -> service.renovarToken());

        verify(tokenRepository, never()).save(any());
    }
}
