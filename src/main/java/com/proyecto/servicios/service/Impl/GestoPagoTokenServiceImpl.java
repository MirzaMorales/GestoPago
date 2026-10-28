package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoAuthClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.mapper.GestoPagoTokenMapper;
import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoTokenRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
public class GestoPagoTokenServiceImpl implements GestoPagoTokenService {

    private final GestoPagoAuthClient gestoPagoAuthClient;
    private final GestoPagoTokenRepository tokenRepository;
    private final GestoPagoTokenMapper tokenMapper;

    @Value("${gestopago.auth.id-distribuidor}")
    private Integer idDistribuidor;

    @Value("${gestopago.auth.codigo-dispositivo}")
    private String codigoDispositivo;

    @Value("${gestopago.auth.password}")
    private String password;

    @Value("${gestopago.auth.edad-maxima-horas:23}")
    private long edadMaximaHoras;

    public GestoPagoTokenServiceImpl(GestoPagoAuthClient gestoPagoAuthClient,
                                     GestoPagoTokenRepository tokenRepository,
                                     GestoPagoTokenMapper tokenMapper) {
        this.gestoPagoAuthClient = gestoPagoAuthClient;
        this.tokenRepository = tokenRepository;
        this.tokenMapper = tokenMapper;
    }

    @Override
    @Scheduled(fixedRateString = "${gestopago.auth.refresh-rate-ms:3600000}", initialDelay = 0)
    public void renovarToken() {
        Optional<GestoPagoToken> existente = tokenRepository
                .findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo);
        if (existente.isPresent() && !estaVencido(existente.get())) {
            log.debug("Token GestoPago vigente; no se solicita uno nuevo");
            return;
        }
        solicitarNuevoToken();
    }

    @Override
    public void renovarTokenForzado() {
        solicitarNuevoToken();
    }

    private boolean estaVencido(GestoPagoToken token) {
        LocalDateTime ultima = token.getFechaActualizacion() != null ? token.getFechaActualizacion() : token.getFechaCreacion();
        return ultima == null || Duration.between(ultima, LocalDateTime.now()).toHours() >= edadMaximaHoras;
    }

    private void solicitarNuevoToken() {
        log.info("Solicitando token GestoPago para distribuidor={}", idDistribuidor);
        try {
            GestoPagoAuthResponse response = gestoPagoAuthClient.authenticate(
                    idDistribuidor, codigoDispositivo, password);

            if (response == null || response.getToken() == null) {
                log.error("La respuesta de GestoPago no contiene token");
                return;
            }

            GestoPagoToken tokenEntity = tokenRepository
                    .findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo)
                    .map(existing -> {
                        tokenMapper.updateEntity(response, existing);
                        return existing;
                    })
                    .orElseGet(() -> {
                        GestoPagoToken nuevo = tokenMapper.toEntity(response);
                        nuevo.setIdDistribuidor(idDistribuidor);
                        nuevo.setCodigoDispositivo(codigoDispositivo);
                        nuevo.setActivo(true);
                        return nuevo;
                    });

            tokenRepository.save(tokenEntity);
            log.info("Token GestoPago renovado correctamente");

        } catch (FeignException e) {
            // No se registra e.getMessage(): incluye la URL de autenticación con la contraseña como parámetro.
            log.error("Error al solicitar token GestoPago: status={}", e.status());
        } catch (Exception e) {
            log.error("Error al solicitar token GestoPago: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public Optional<GestoPagoToken> obtenerTokenActivo(Integer idDistribuidor, String codigoDispositivo) {
        return tokenRepository.findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo);
    }
}
