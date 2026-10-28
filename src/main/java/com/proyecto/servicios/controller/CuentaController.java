package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.request.AbonoCuentaRequest;
import com.proyecto.servicios.model.request.BuscaCuentaRequest;
import com.proyecto.servicios.model.request.ConsultaSaldoRequest;
import com.proyecto.servicios.model.request.EstablecePinRequest;
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

    /** Abona saldo a una cuenta; solo ADMIN. */
    @PostMapping("/abono")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GenericResponse<SaldoResponse>> abonar(@Valid @RequestBody AbonoCuentaRequest request) {
        SaldoResponse saldo = cuentaService.abonar(request);
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Abono realizado correctamente", saldo));
    }

    /** Configura o cambia el PIN de autorización de pagos; solo el titular de la cuenta. */
    @PostMapping("/pin")
    @PreAuthorize("@accesos.esDuenoDeCuenta(#request.numeroCuenta)")
    public ResponseEntity<GenericResponse<Void>> establecerPin(@Valid @RequestBody EstablecePinRequest request) {
        cuentaService.establecerPin(request);
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "PIN de autorización configurado correctamente", null));
    }
}
