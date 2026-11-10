package com.proyecto.servicios.service;

import com.proyecto.servicios.model.response.CompraResponse;
import com.proyecto.servicios.model.response.EstadoCompraResponse;
import com.proyecto.servicios.model.response.PaginaResponse;

public interface HistorialService {

    /** Compras EXITOSAS de todas las cuentas del cliente del usuario, de la más reciente a la más antigua. */
    PaginaResponse<CompraResponse> listar(String correoUsuario, int pagina, int tamano);

    /** Detalle de una compra exitosa del usuario; falla con CompraNoEncontradaException si no es suya o no fue exitosa. */
    CompraResponse detalle(String correoUsuario, Long idTransaccion);

    /** Estado actual (exitosa, rechazada o pendiente) de una compra del usuario, para consultar una que quedó pendiente. */
    EstadoCompraResponse estado(String correoUsuario, Long idTransaccion);
}
