package com.proyecto.servicios.repositorys;

import com.proyecto.servicios.entity.EstatusTransaccion;
import com.proyecto.servicios.entity.TransaccionPago;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransaccionPagoRepository extends JpaRepository<TransaccionPago, Long> {

    Optional<TransaccionPago> findByCuentaIdAndIdempotencyKey(Long cuentaId, String idempotencyKey);

    /** Bloquea la fila de la transacción para resolverla (confirmar/revertir) sin carreras entre instancias. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransaccionPago t WHERE t.id = :id")
    Optional<TransaccionPago> findByIdParaActualizar(@Param("id") Long id);

    /** Hay una operación idéntica sin resolver (en proceso o en revisión) para la misma cuenta. */
    boolean existsByCuentaIdAndIdProductoAndTelefonoReferenciaAndMontoAndEstatusIn(
            Long cuentaId, Integer idProducto, String telefonoReferencia, BigDecimal monto,
            Collection<EstatusTransaccion> estatus);

    List<TransaccionPago> findByEstatusInOrderByFechaCreacionAsc(Collection<EstatusTransaccion> estatus);

    /**
     * Candidatas a conciliar con confirmTx: sin resolver, enviadas hace más de la espera mínima
     * (el proveedor no permite confirmar antes de 62 s) y dentro de la ventana de 24 h.
     */
    @Query("""
            SELECT t.id FROM TransaccionPago t
            WHERE t.estatus IN :estatus
              AND t.fechaEnvio IS NOT NULL
              AND t.fechaEnvio <= :limiteSuperior
              AND t.fechaEnvio >= :limiteInferior
            ORDER BY t.fechaEnvio ASC
            """)
    List<Long> findIdsParaConciliar(@Param("estatus") Collection<EstatusTransaccion> estatus,
                                    @Param("limiteSuperior") OffsetDateTime limiteSuperior,
                                    @Param("limiteInferior") OffsetDateTime limiteInferior);
}
