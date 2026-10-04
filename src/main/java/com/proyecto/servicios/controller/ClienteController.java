package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.request.ActualizaClienteRequest;
import com.proyecto.servicios.model.request.RegistroClienteRequest;
import com.proyecto.servicios.model.response.ClienteResponse;
import com.proyecto.servicios.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/clientes")
public class ClienteController {

    @Autowired
    private ClienteService clienteService;

    @PostMapping
    public ResponseEntity<ClienteResponse> registrarCliente(@Valid @RequestBody RegistroClienteRequest request) {
        ClienteResponse response = clienteService.registrarCliente(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<ClienteResponse>> obtenerClientes(
            @RequestParam(required = false) Boolean activos,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin
    ) {
        if (fechaInicio != null && fechaFin != null) {
            return ResponseEntity.ok(clienteService.obtenerClientesPorRangoFechas(fechaInicio, fechaFin));
        }
        if (Boolean.TRUE.equals(activos)) {
            return ResponseEntity.ok(clienteService.obtenerClientesActivos());
        }
        return ResponseEntity.ok(clienteService.obtenerTodosLosClientes());
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<ClienteResponse>> buscarClientesDinamico(
            @RequestParam(required = false) String curp,
            @RequestParam(required = false) String rfc,
            @RequestParam(required = false) String correo,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String apellido,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false) String numeroCuenta
    ) {
        List<ClienteResponse> resultados = clienteService.buscarClientesDinamico(
                curp, rfc, correo, nombre, apellido, activo, fechaInicio, fechaFin, numeroCuenta
        );
        return ResponseEntity.ok(resultados);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponse> obtenerClientePorId(@PathVariable Long id) {
        return ResponseEntity.ok(clienteService.obtenerClientePorId(id));
    }

    @GetMapping("/curp/{curp}")
    public ResponseEntity<ClienteResponse> obtenerClientePorCurp(@PathVariable String curp) {
        return ResponseEntity.ok(clienteService.obtenerClientePorCurp(curp));
    }

    @GetMapping("/rfc/{rfc}")
    public ResponseEntity<ClienteResponse> obtenerClientePorRfc(@PathVariable String rfc) {
        return ResponseEntity.ok(clienteService.obtenerClientePorRfc(rfc));
    }

    @GetMapping("/correo/{correo}")
    public ResponseEntity<ClienteResponse> obtenerClientePorCorreo(@PathVariable String correo) {
        return ResponseEntity.ok(clienteService.obtenerClientePorCorreo(correo));
    }

    @GetMapping("/cuenta/{numeroCuenta}")
    public ResponseEntity<ClienteResponse> obtenerClientePorNumeroCuenta(@PathVariable String numeroCuenta) {
        return ResponseEntity.ok(clienteService.obtenerClientePorNumeroCuenta(numeroCuenta));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteResponse> actualizarCliente(
            @PathVariable Long id,
            @Valid @RequestBody ActualizaClienteRequest request
    ) {
        ClienteResponse response = clienteService.actualizarCliente(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> darDeBajaLogicaCliente(@PathVariable Long id) {
        clienteService.darDeBajaLogicaCliente(id);
        return ResponseEntity.noContent().build();
    }
}
