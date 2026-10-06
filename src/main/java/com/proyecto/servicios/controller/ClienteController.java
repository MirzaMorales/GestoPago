package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.request.ActualizaClienteRequest;
import com.proyecto.servicios.model.request.RegistroClienteRequest;
import com.proyecto.servicios.model.response.ClienteResponse;
import com.proyecto.servicios.exception.ValidacionNegocioException;
import com.proyecto.servicios.service.ClienteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@Validated
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
        validarRangoFechas(fechaInicio, fechaFin);
        if ((fechaInicio == null) != (fechaFin == null)) {
            throw new ValidacionNegocioException("Debe proporcionar fechaInicio y fechaFin juntas");
        }
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
            @RequestParam(required = false) @Pattern(regexp = "(?iu)^[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z0-9]\\d$", message = "Formato de CURP inválido") String curp,
            @RequestParam(required = false) @Pattern(regexp = "(?iu)^[A-Z&Ñ]{3,4}\\d{6}[A-Z0-9]{3}$", message = "Formato de RFC inválido") String rfc,
            @RequestParam(required = false) @Size(max = 100, message = "El correo electrónico no puede exceder 100 caracteres") String correo,
            @RequestParam(required = false) @Size(max = 50, message = "El nombre no puede exceder 50 caracteres") String nombre,
            @RequestParam(required = false) @Size(max = 50, message = "El apellido no puede exceder 50 caracteres") String apellido,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false) @Pattern(regexp = "^\\d{16}$", message = "El número de cuenta debe contener 16 dígitos") String numeroCuenta
    ) {
        validarRangoFechas(fechaInicio, fechaFin);
        List<ClienteResponse> resultados = clienteService.buscarClientesDinamico(
                curp, rfc, correo, nombre, apellido, activo, fechaInicio, fechaFin, numeroCuenta
        );
        return ResponseEntity.ok(resultados);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponse> obtenerClientePorId(@PathVariable @Positive(message = "El ID debe ser positivo") Long id) {
        return ResponseEntity.ok(clienteService.obtenerClientePorId(id));
    }

    @GetMapping("/curp/{curp}")
    public ResponseEntity<ClienteResponse> obtenerClientePorCurp(
            @PathVariable @Pattern(regexp = "(?iu)^[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z0-9]\\d$", message = "Formato de CURP inválido") String curp
    ) {
        return ResponseEntity.ok(clienteService.obtenerClientePorCurp(curp));
    }

    @GetMapping("/rfc/{rfc}")
    public ResponseEntity<ClienteResponse> obtenerClientePorRfc(
            @PathVariable @Pattern(regexp = "(?iu)^[A-Z&Ñ]{3,4}\\d{6}[A-Z0-9]{3}$", message = "Formato de RFC inválido") String rfc
    ) {
        return ResponseEntity.ok(clienteService.obtenerClientePorRfc(rfc));
    }

    @GetMapping("/correo/{correo}")
    public ResponseEntity<ClienteResponse> obtenerClientePorCorreo(
            @PathVariable @Email(message = "El formato del correo electrónico es inválido")
            @Size(max = 100, message = "El correo electrónico no puede exceder 100 caracteres") String correo
    ) {
        return ResponseEntity.ok(clienteService.obtenerClientePorCorreo(correo));
    }

    @GetMapping("/cuenta/{numeroCuenta}")
    public ResponseEntity<ClienteResponse> obtenerClientePorNumeroCuenta(
            @PathVariable @Pattern(regexp = "^\\d{16}$", message = "El número de cuenta debe contener 16 dígitos") String numeroCuenta
    ) {
        return ResponseEntity.ok(clienteService.obtenerClientePorNumeroCuenta(numeroCuenta));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteResponse> actualizarCliente(
            @PathVariable @Positive(message = "El ID debe ser positivo") Long id,
            @Valid @RequestBody ActualizaClienteRequest request
    ) {
        ClienteResponse response = clienteService.actualizarCliente(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> darDeBajaLogicaCliente(
            @PathVariable @Positive(message = "El ID debe ser positivo") Long id
    ) {
        clienteService.darDeBajaLogicaCliente(id);
        return ResponseEntity.noContent().build();
    }

    private void validarRangoFechas(LocalDate fechaInicio, LocalDate fechaFin) {
        if (fechaInicio != null && fechaFin != null && fechaInicio.isAfter(fechaFin)) {
            throw new ValidacionNegocioException("fechaInicio no puede ser posterior a fechaFin");
        }
    }
}
