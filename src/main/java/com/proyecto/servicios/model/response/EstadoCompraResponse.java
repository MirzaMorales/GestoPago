package com.proyecto.servicios.model.response;

import com.proyecto.servicios.model.EstadoCompra;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Estado actual de una compra: sirve para saber en qué terminó una compra que quedó pendiente. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstadoCompraResponse {
    private Long idTransaccion;
    private EstadoCompra estadoCompra;
    private String mensaje;
}
