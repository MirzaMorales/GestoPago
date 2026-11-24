package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.request.AbonoCuentaRequest;
import com.proyecto.servicios.model.request.BuscaCuentaRequest;
import com.proyecto.servicios.model.request.ConsultaSaldoRequest;
import com.proyecto.servicios.model.request.EstablecePinRequest;
import com.proyecto.servicios.model.request.PinCuentaRequests;
import com.proyecto.servicios.model.response.Login2faResponse;
import com.proyecto.servicios.security.Otp2faService;
import com.proyecto.servicios.service.PinSeguridadService;
import java.util.Map;
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

    @Autowired
    private PinSeguridadService pinSeguridad;

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

    /** "Olvidé mi PIN": genera un PIN temporal y lo envía al correo del titular. */
    @PostMapping("/pin/olvide")
    @PreAuthorize("@accesos.esDuenoDeCuenta(#request.numeroCuenta)")
    public ResponseEntity<GenericResponse<Void>> olvidePin(@Valid @RequestBody PinCuentaRequests.Olvide request) {
        pinSeguridad.olvide(request.getNumeroCuenta());
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK,
                "Te enviamos un PIN temporal a tu correo. Cámbialo después de tu primera compra", null));
    }

    /** Cambio de PIN, paso 1: valida el PIN actual y envía un código al correo. */
    @PostMapping("/pin/cambio/iniciar")
    @PreAuthorize("@accesos.esDuenoDeCuenta(#request.numeroCuenta)")
    public ResponseEntity<Login2faResponse> iniciarCambioPin(@Valid @RequestBody PinCuentaRequests.IniciarCambio request) {
        Otp2faService.Desafio d = pinSeguridad.iniciarCambio(request.getNumeroCuenta(), request.getPinActual());
        return ResponseEntity.ok(Login2faResponse.builder()
                .requiere2fa(true)
                .desafioId(d.desafioId())
                .correoEnmascarado(d.correoEnmascarado())
                .expiraEnSegundos(d.expiraEnSegundos())
                .build());
    }

    /** Cambio de PIN, paso 2: valida el código y entrega la autorización para fijar el PIN nuevo. */
    @PostMapping("/pin/cambio/verificar")
    @PreAuthorize("@accesos.esDuenoDeCuenta(#request.numeroCuenta)")
    public ResponseEntity<GenericResponse<Map<String, String>>> verificarCambioPin(
            @Valid @RequestBody PinCuentaRequests.VerificarCambio request) {
        String autorizacionId = pinSeguridad.confirmarCodigo(
                request.getNumeroCuenta(), request.getDesafioId(), request.getCodigo());
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Verificación correcta",
                Map.of("autorizacionId", autorizacionId)));
    }
}
