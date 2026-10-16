package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.request.BuscaCuentaRequest;
import com.proyecto.servicios.model.request.ConsultaSaldoRequest;
import com.proyecto.servicios.model.response.CuentaResponse;
import com.proyecto.servicios.model.response.SaldoResponse;
import com.proyecto.servicios.service.CuentaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cuentas")
public class CuentaController {

    @Autowired
    private CuentaService cuentaService;

    /**
     * Único endpoint de consulta de cuentas. ADMIN puede listar todas o filtrar;
     * un CLIENTE solo puede consultar una cuenta propia indicando numeroCuenta.
     */
    @PostMapping("/buscar")
    @PreAuthorize("hasRole('ADMIN') or (#request.numeroCuenta != null and @accesos.esDuenoDeCuenta(#request.numeroCuenta))")
    public ResponseEntity<GenericResponse<List<CuentaResponse>>> buscarCuentas(@Valid @RequestBody BuscaCuentaRequest request) {
        List<CuentaResponse> cuentas = cuentaService.buscarCuentas(request);
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Consulta realizada correctamente", cuentas));
    }

    @PostMapping("/saldo")
    @PreAuthorize("hasRole('ADMIN') or @accesos.esDuenoDeCuenta(#request.numeroCuenta)")
    public ResponseEntity<GenericResponse<SaldoResponse>> obtenerSaldoCuenta(@Valid @RequestBody ConsultaSaldoRequest request) {
        SaldoResponse saldo = cuentaService.obtenerSaldoCuenta(request.getNumeroCuenta());
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Saldo consultado correctamente", saldo));
    }
}
