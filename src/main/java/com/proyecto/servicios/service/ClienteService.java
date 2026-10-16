package com.proyecto.servicios.service;

import com.proyecto.servicios.model.request.ActualizaClienteRequest;
import com.proyecto.servicios.model.request.BuscaClienteRequest;
import com.proyecto.servicios.model.request.RegistroClienteRequest;
import com.proyecto.servicios.model.response.ClienteResponse;

import java.util.List;

public interface ClienteService {

    ClienteResponse registrarCliente(RegistroClienteRequest request);

    List<ClienteResponse> buscarClientesDinamico(BuscaClienteRequest criterios);

    ClienteResponse actualizarCliente(ActualizaClienteRequest request);

    void darDeBajaLogicaCliente(Long id);
}
