package com.proyecto.servicios.service;

import com.proyecto.servicios.model.request.ResolverConciliacionRequest;
import com.proyecto.servicios.model.response.TransaccionPendienteResponse;
import com.proyecto.servicios.service.PagoTransaccionService.ResultadoConciliacion;

import java.util.List;

/**
 * Conciliación de operaciones cuyo resultado se desconoce (timeout, CODIGO 82, caída tras el envío).
 * Usa confirmTx del proveedor con el mismo payload original; nunca reenvía sendTx.
 */
public interface ConciliacionService {

    /** Concilia todas las transacciones elegibles; devuelve cuántas se procesaron. */
    int conciliarPendientes();

    ResultadoConciliacion conciliar(Long transaccionId);

    List<TransaccionPendienteResponse> listarPendientes();

    ResultadoConciliacion resolverManual(ResolverConciliacionRequest request, String usuario);
}
