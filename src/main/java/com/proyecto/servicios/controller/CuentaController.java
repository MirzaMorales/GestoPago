package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.response.CuentaResponse;
import com.proyecto.servicios.model.response.SaldoResponse;
import com.proyecto.servicios.service.CuentaService;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequestMapping("/cuentas")
public class CuentaController {

    @Autowired
    private CuentaService cuentaService;

    @GetMapping("/{numeroCuenta}")
    public ResponseEntity<CuentaResponse> obtenerCuentaPorNumero(
            @PathVariable @Pattern(regexp = "^\\d{16}$", message = "El número de cuenta debe contener 16 dígitos") String numeroCuenta
    ) {
        return ResponseEntity.ok(cuentaService.obtenerCuentaPorNumero(numeroCuenta));
    }

    @GetMapping("/activas")
    public ResponseEntity<List<CuentaResponse>> obtenerCuentasActivas() {
        return ResponseEntity.ok(cuentaService.obtenerCuentasActivas());
    }

    @GetMapping("/{numeroCuenta}/saldo")
    public ResponseEntity<SaldoResponse> obtenerSaldoCuenta(
            @PathVariable @Pattern(regexp = "^\\d{16}$", message = "El número de cuenta debe contener 16 dígitos") String numeroCuenta
    ) {
        return ResponseEntity.ok(cuentaService.obtenerSaldoCuenta(numeroCuenta));
    }
}
