package com.proyecto.servicios.service;

import com.proyecto.servicios.model.request.ActualizaClienteRequest;
import com.proyecto.servicios.model.request.RegistroClienteRequest;
import com.proyecto.servicios.model.response.ClienteResponse;

import java.time.LocalDate;
import java.util.List;

public interface ClienteService {

    ClienteResponse registrarCliente(RegistroClienteRequest request);

    List<ClienteResponse> obtenerTodosLosClientes();

    List<ClienteResponse> obtenerClientesActivos();

    ClienteResponse obtenerClientePorId(Long id);

    ClienteResponse obtenerClientePorCurp(String curp);

    ClienteResponse obtenerClientePorRfc(String rfc);

    ClienteResponse obtenerClientePorCorreo(String correo);

    ClienteResponse obtenerClientePorNumeroCuenta(String numeroCuenta);

    List<ClienteResponse> obtenerClientesPorRangoFechas(LocalDate fechaInicio, LocalDate fechaFin);

    List<ClienteResponse> buscarClientesDinamico(
            String curp,
            String rfc,
            String correo,
            String nombre,
            String apellido,
            Boolean activo,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            String numeroCuenta
    );

    ClienteResponse actualizarCliente(Long id, ActualizaClienteRequest request);

    void darDeBajaLogicaCliente(Long id);
}
