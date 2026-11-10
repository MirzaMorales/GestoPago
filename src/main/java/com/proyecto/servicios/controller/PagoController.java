package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.request.PagoServicioRequest;
import com.proyecto.servicios.model.response.CompraResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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

    /** Historial de compras exitosas del usuario autenticado (paginado, más recientes primero). */
    @GetMapping("/historial")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<GenericResponse<PaginaResponse<CompraResponse>>> historial(
            Authentication auth,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Historial de compras",
                historialService.listar(auth.getName(), pagina, tamano)));
    }

    /** Detalle de una compra exitosa del usuario autenticado. */
    @GetMapping("/historial/{idTransaccion}")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<GenericResponse<CompraResponse>> detalleCompra(
            Authentication auth, @PathVariable Long idTransaccion) {
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Detalle de la compra",
                historialService.detalle(auth.getName(), idTransaccion)));
    }
}
