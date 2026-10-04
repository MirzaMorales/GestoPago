package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.Cliente;
import com.proyecto.servicios.entity.Cuenta;
import com.proyecto.servicios.entity.Domicilio;
import com.proyecto.servicios.entity.Usuario;
import com.proyecto.servicios.model.request.DomicilioRequest;
import com.proyecto.servicios.model.request.RegistroClienteRequest;
import com.proyecto.servicios.model.response.ClienteResponse;
import com.proyecto.servicios.model.response.CuentaResponse;
import com.proyecto.servicios.model.response.DomicilioResponse;
import com.proyecto.servicios.model.response.UsuarioResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.stream.Collectors;

@Component
public class ClienteMapper {

    public Domicilio toDomicilioEntity(DomicilioRequest request) {
        if (request == null) return null;
        return Domicilio.builder()
                .calle(request.getCalle().trim())
                .numeroExterior(request.getNumeroExterior().trim())
                .numeroInterior(request.getNumeroInterior() != null ? request.getNumeroInterior().trim() : null)
                .colonia(request.getColonia().trim())
                .municipio(request.getMunicipio().trim())
                .estado(request.getEstado().trim())
                .codigoPostal(request.getCodigoPostal().trim())
                .pais(request.getPais() != null && !request.getPais().isBlank() ? request.getPais().trim() : "México")
                .build();
    }

    public DomicilioResponse toDomicilioResponse(Domicilio entity) {
        if (entity == null) return null;
        return DomicilioResponse.builder()
                .id(entity.getId())
                .calle(entity.getCalle())
                .numeroExterior(entity.getNumeroExterior())
                .numeroInterior(entity.getNumeroInterior())
                .colonia(entity.getColonia())
                .municipio(entity.getMunicipio())
                .estado(entity.getEstado())
                .codigoPostal(entity.getCodigoPostal())
                .pais(entity.getPais())
                .build();
    }

    public Cliente toClienteEntity(RegistroClienteRequest request, Domicilio domicilio) {
        if (request == null) return null;
        return Cliente.builder()
                .nombre(request.getNombre().trim())
                .segundoNombre(request.getSegundoNombre() != null ? request.getSegundoNombre().trim() : null)
                .apellidoPaterno(request.getApellidoPaterno().trim())
                .apellidoMaterno(request.getApellidoMaterno().trim())
                .fechaNacimiento(request.getFechaNacimiento())
                .curp(request.getCurp().trim().toUpperCase())
                .rfc(request.getRfc().trim().toUpperCase())
                .sexo(request.getSexo().trim())
                .nacionalidad(request.getNacionalidad() != null && !request.getNacionalidad().isBlank() ? request.getNacionalidad().trim() : "Mexicana")
                .estadoCivil(request.getEstadoCivil().trim())
                .correo(request.getCorreo().trim().toLowerCase())
                .telefonoMovil(request.getTelefonoMovil().trim())
                .telefonoAlternativo(request.getTelefonoAlternativo() != null ? request.getTelefonoAlternativo().trim() : null)
                .ocupacion(request.getOcupacion().trim())
                .empresa(request.getEmpresa().trim())
                .ingresoMensual(request.getIngresoMensual())
                .domicilio(domicilio)
                .activo(true)
                .build();
    }

    public CuentaResponse toCuentaResponse(Cuenta entity) {
        if (entity == null) return null;
        return CuentaResponse.builder()
                .id(entity.getId())
                .numeroCuenta(entity.getNumeroCuenta())
                .clienteId(entity.getCliente() != null ? entity.getCliente().getId() : null)
                .saldo(entity.getSaldo())
                .estatus(entity.getEstatus())
                .fechaApertura(entity.getFechaApertura())
                .fechaActualizacion(entity.getFechaActualizacion())
                .build();
    }

    public UsuarioResponse toUsuarioResponse(Usuario entity) {
        if (entity == null) return null;
        return UsuarioResponse.builder()
                .id(entity.getId())
                .clienteId(entity.getCliente() != null ? entity.getCliente().getId() : null)
                .correo(entity.getCorreo())
                .activo(entity.getActivo())
                .fechaCreacion(entity.getFechaCreacion())
                .fechaActualizacion(entity.getFechaActualizacion())
                .build();
    }

    public ClienteResponse toClienteResponse(Cliente entity) {
        if (entity == null) return null;
        return ClienteResponse.builder()
                .id(entity.getId())
                .nombre(entity.getNombre())
                .segundoNombre(entity.getSegundoNombre())
                .apellidoPaterno(entity.getApellidoPaterno())
                .apellidoMaterno(entity.getApellidoMaterno())
                .fechaNacimiento(entity.getFechaNacimiento())
                .curp(entity.getCurp())
                .rfc(entity.getRfc())
                .sexo(entity.getSexo())
                .nacionalidad(entity.getNacionalidad())
                .estadoCivil(entity.getEstadoCivil())
                .correo(entity.getCorreo())
                .telefonoMovil(entity.getTelefonoMovil())
                .telefonoAlternativo(entity.getTelefonoAlternativo())
                .ocupacion(entity.getOcupacion())
                .empresa(entity.getEmpresa())
                .ingresoMensual(entity.getIngresoMensual())
                .domicilio(toDomicilioResponse(entity.getDomicilio()))
                .activo(entity.getActivo())
                .fechaCreacion(entity.getFechaCreacion())
                .fechaActualizacion(entity.getFechaActualizacion())
                .cuentas(entity.getCuentas() != null ?
                        entity.getCuentas().stream().map(this::toCuentaResponse).collect(Collectors.toList()) : Collections.emptyList())
                .usuario(toUsuarioResponse(entity.getUsuario()))
                .build();
    }
}
