package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.request.PagoServicioRequest;
import com.proyecto.servicios.model.response.PagoServicioResponse;
import com.proyecto.servicios.service.PagoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    /** Pago de servicios: solo el titular de la cuenta puede pagar con ella. */
    @PostMapping("/servicio")
    @PreAuthorize("@accesos.esDuenoDeCuenta(#request.numeroCuenta)")
    public ResponseEntity<GenericResponse<PagoServicioResponse>> pagarServicio(
            @Valid @RequestBody PagoServicioRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        PagoServicioResponse resultado = pagoService.pagarServicio(request, idempotencyKey);
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Pago realizado correctamente", resultado));
    }
}
