package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.request.DetalleCompraRequest;
import com.proyecto.servicios.model.request.HistorialRequest;
import com.proyecto.servicios.model.request.PagoServicioRequest;
import com.proyecto.servicios.model.response.CompraResponse;
import com.proyecto.servicios.model.response.EstadoCompraResponse;
import com.proyecto.servicios.model.response.PagoServicioResponse;
import com.proyecto.servicios.model.response.PaginaResponse;
import com.proyecto.servicios.service.HistorialService;
import com.proyecto.servicios.service.PagoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pagos")
public class PagoController {

    @Autowired
    private PagoService pagoService;

    @Autowired
    private HistorialService historialService;

    /** Pago de servicios: solo el titular de la cuenta puede pagar con ella. */
    @PostMapping("/servicio")
    @PreAuthorize("@accesos.esDuenoDeCuenta(#request.numeroCuenta)")
    public ResponseEntity<GenericResponse<PagoServicioResponse>> pagarServicio(
            @Valid @RequestBody PagoServicioRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        PagoServicioResponse resultado = pagoService.pagarServicio(request, idempotencyKey);
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Pago realizado correctamente", resultado));
    }

    /** Historial de compras exitosas del usuario autenticado (paginado, más recientes primero). Datos en el body. */
    @PostMapping("/historial")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<GenericResponse<PaginaResponse<CompraResponse>>> historial(
            Authentication auth,
            @Valid @RequestBody(required = false) HistorialRequest request) {
        HistorialRequest r = request != null ? request : new HistorialRequest();
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Historial de compras",
                historialService.listar(auth.getName(), r.getPagina(), r.getTamano())));
    }

    /** Detalle de una compra exitosa del usuario autenticado. El identificador viaja en el body, no en la URL. */
    @PostMapping("/historial/detalle")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<GenericResponse<CompraResponse>> detalleCompra(
            Authentication auth, @Valid @RequestBody DetalleCompraRequest request) {
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Detalle de la compra",
                historialService.detalle(auth.getName(), request.getIdTransaccion())));
    }

    /** Estado actual de una compra del usuario (útil para saber en qué terminó una compra pendiente). Id en el body. */
    @PostMapping("/estado")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<GenericResponse<EstadoCompraResponse>> estadoCompra(
            Authentication auth, @Valid @RequestBody DetalleCompraRequest request) {
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Estado de la compra",
                historialService.estado(auth.getName(), request.getIdTransaccion())));
    }
}
