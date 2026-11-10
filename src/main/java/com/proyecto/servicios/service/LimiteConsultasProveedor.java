package com.proyecto.servicios.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Tope diario de consultas a getProductList. El proveedor permite máximo 3 al día y bloquea la IP si se excede,
 * así que cada consulta en vivo (con éxito o fallida) consume cupo y, agotado, ya no se llama al proveedor.
 */
@Slf4j
@Service
public class LimiteConsultasProveedor {

    private static final String PREFIJO = "gestopago:getProductList:consultas:";

    private final StringRedisTemplate redis;
    private final int maxPorDia;
    private final ZoneId zona;

    public LimiteConsultasProveedor(StringRedisTemplate redis,
                                    @Value("${gestopago.catalogo.max-consultas-dia:2}") int maxPorDia,
                                    @Value("${gestopago.tx.zona-horaria:America/Mexico_City}") String zona) {
        this.redis = redis;
        this.maxPorDia = maxPorDia;
        this.zona = ZoneId.of(zona);
    }

    /** Reserva una consulta del día. false si ya se agotó el cupo (o si no se puede comprobar: ante la duda no se llama). */
    public boolean reservar() {
        try {
            String clave = PREFIJO + LocalDate.now(zona);
            Long consultas = redis.opsForValue().increment(clave);
            if (consultas != null && consultas == 1) {
                redis.expire(clave, Duration.ofHours(30));
            }
            boolean permitida = consultas != null && consultas <= maxPorDia;
            if (!permitida) {
                log.warn("Cupo diario de getProductList agotado ({} de {}): no se consulta al proveedor", consultas, maxPorDia);
            }
            return permitida;
        } catch (RuntimeException ex) {
            log.error("No se pudo comprobar el cupo de getProductList: {}", ex.getClass().getSimpleName());
            return false;
        }
    }
}
