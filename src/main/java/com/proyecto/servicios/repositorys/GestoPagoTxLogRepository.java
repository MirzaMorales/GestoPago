package com.proyecto.servicios.repositorys;

import com.proyecto.servicios.entity.GestoPagoTxLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GestoPagoTxLogRepository extends JpaRepository<GestoPagoTxLog, Long> {

    List<GestoPagoTxLog> findByTransaccionIdOrderByFechaAsc(Long transaccionId);
}
