package com.proyecto.servicios.service;

import com.proyecto.servicios.model.request.HistorialRequest;
import com.proyecto.servicios.model.response.CompraResponse;
import com.proyecto.servicios.model.response.EstadoCompraResponse;
import com.proyecto.servicios.model.response.PaginaResponse;

public interface HistorialService {

    /**
     * Compras de todas las cuentas del cliente del usuario (exitosas, rechazadas y pendientes), de la más reciente
     * a la más antigua, con filtros opcionales de estado y rango de fechas.
     */
    PaginaResponse<CompraResponse> listar(String correoUsuario, HistorialRequest filtros);

    /** Atajo sin filtros (todas las compras). */
    default PaginaResponse<CompraResponse> listar(String correoUsuario, int pagina, int tamano) {
        return listar(correoUsuario, HistorialRequest.builder().pagina(pagina).tamano(tamano).build());
    }

    /** Detalle de una compra del usuario en cualquier estado; CompraNoEncontradaException si no es suya. */
    CompraResponse detalle(String correoUsuario, Long idTransaccion);

    /** Estado actual (exitosa, rechazada o pendiente) de una compra del usuario, para consultar una que quedó pendiente. */
    EstadoCompraResponse estado(String correoUsuario, Long idTransaccion);
}
