package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.GestoPagoTxLog;
import com.proyecto.servicios.repositorys.GestoPagoTxLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Guarda en base de datos lo enviado y recibido de GestoPago (requisito del proveedor para aclaraciones). */
@Service
public class TxLogService {

    public static final String SEND_TX = "SEND_TX";
    public static final String CONFIRM_TX = "CONFIRM_TX";

    @Autowired
    private GestoPagoTxLogRepository repository;

    @Transactional
    public void registrar(Long transaccionId, String operacion, String payloadEnviado, String respuesta, String resultado) {
        repository.save(GestoPagoTxLog.builder()
                .transaccionId(transaccionId)
                .operacion(operacion)
                .payloadEnviado(payloadEnviado)
                .respuesta(respuesta)
                .resultado(resultado)
                .build());
    }
}
