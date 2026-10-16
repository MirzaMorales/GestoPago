package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.ValidacionNegocioException;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.request.ActualizaClienteRequest;
import com.proyecto.servicios.model.request.BuscaClienteRequest;
import com.proyecto.servicios.model.request.ClienteIdRequest;
import com.proyecto.servicios.model.request.RegistroClienteRequest;
import com.proyecto.servicios.model.response.ClienteResponse;
import com.proyecto.servicios.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/clientes")
public class ClienteController {

    @Autowired
    private ClienteService clienteService;

    @PostMapping
    public ResponseEntity<GenericResponse<ClienteResponse>> registrarCliente(@Valid @RequestBody RegistroClienteRequest request) {
        ClienteResponse response = clienteService.registrarCliente(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GenericResponse.ok(HttpStatus.CREATED, "Cliente registrado correctamente", response));
    }

    /**
     * Único endpoint de consulta: todos los criterios del body son opcionales y combinables
     * (incluido el id). ADMIN puede buscar por cualquier criterio; un CLIENTE solo puede
     * consultar su propio id y el resto de criterios se ignora.
     * Banderas incluirDomicilio, incluirCuentas e incluirUsuario controlan el detalle de la respuesta.
     */
    @PostMapping("/buscar")
    @PreAuthorize("hasRole('ADMIN') or (#request.id != null and @accesos.esClienteId(#request.id))")
    public ResponseEntity<GenericResponse<List<ClienteResponse>>> buscarClientes(
            @Valid @RequestBody BuscaClienteRequest request,
            Authentication authentication
    ) {
        if (request.getFechaInicio() != null && request.getFechaFin() != null
                && request.getFechaInicio().isAfter(request.getFechaFin())) {
            throw new ValidacionNegocioException("fechaInicio no puede ser posterior a fechaFin");
        }
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        BuscaClienteRequest criterios = admin ? request : BuscaClienteRequest.builder()
                .id(request.getId())
                .incluirDomicilio(request.isIncluirDomicilio())
                .incluirCuentas(request.isIncluirCuentas())
                .incluirUsuario(request.isIncluirUsuario())
                .build();

        List<ClienteResponse> clientes = clienteService.buscarClientesDinamico(criterios);
        clientes.forEach(c -> aplicarBanderas(c, criterios));
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Consulta realizada correctamente", clientes));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN') or @accesos.esClienteId(#request.id)")
    public ResponseEntity<GenericResponse<ClienteResponse>> actualizarCliente(
            @Valid @RequestBody ActualizaClienteRequest request
    ) {
        ClienteResponse response = clienteService.actualizarCliente(request);
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Cliente actualizado correctamente", response));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GenericResponse<Void>> darDeBajaLogicaCliente(
            @Valid @RequestBody ClienteIdRequest request
    ) {
        clienteService.darDeBajaLogicaCliente(request.getId());
        return ResponseEntity.ok(GenericResponse.ok(HttpStatus.OK, "Cliente dado de baja correctamente", null));
    }

    /** Las banderas incluir* quitan de la respuesta las secciones que el consumidor no necesita. */
    private void aplicarBanderas(ClienteResponse cliente, BuscaClienteRequest criterios) {
        if (!criterios.isIncluirDomicilio()) {
            cliente.setDomicilio(null);
        }
        if (!criterios.isIncluirCuentas()) {
            cliente.setCuentas(null);
        }
        if (!criterios.isIncluirUsuario()) {
            cliente.setUsuario(null);
        }
    }
}
