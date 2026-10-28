package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.request.ResolverConciliacionRequest;
import com.proyecto.servicios.model.response.TransaccionPendienteResponse;
import com.proyecto.servicios.service.ConciliacionService;
import com.proyecto.servicios.service.PagoTransaccionService.ResultadoConciliacion;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Conciliación de pagos con resultado desconocido. Solo ADMIN. */
@RestController
@RequestMapping("/pagos/conciliacion")
@PreAuthorize("hasRole('ADMIN')")
public class ConciliacionController {

    @Autowired
    private ConciliacionService conciliacionService;

    @PostMapping("/pendientes")
    public ResponseEntity<GenericResponse<List<TransaccionPendienteResponse>>> pendientes() {
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Consulta realizada correctamente",
                conciliacionService.listarPendientes()));
    }

    /** Ejecuta ya la conciliación automática con confirmTx (también corre periódicamente). */
    @PostMapping("/ejecutar")
    public ResponseEntity<GenericResponse<Map<String, Integer>>> ejecutar() {
        int procesadas = conciliacionService.conciliarPendientes();
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Conciliación ejecutada",
                Map.of("transaccionesProcesadas", procesadas)));
    }

    @PostMapping("/resolver")
    public ResponseEntity<GenericResponse<Map<String, String>>> resolver(@Valid @RequestBody ResolverConciliacionRequest request,
                                                                          Authentication authentication) {
        ResultadoConciliacion resultado = conciliacionService.resolverManual(request, authentication.getName());
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Transacción resuelta",
                Map.of("resultado", resultado.name())));
    }
}
