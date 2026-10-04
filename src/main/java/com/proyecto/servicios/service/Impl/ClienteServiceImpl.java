package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.*;
import com.proyecto.servicios.exception.*;
import com.proyecto.servicios.mapper.ClienteMapper;
import com.proyecto.servicios.model.request.ActualizaClienteRequest;
import com.proyecto.servicios.model.request.RegistroClienteRequest;
import com.proyecto.servicios.model.response.ClienteResponse;
import com.proyecto.servicios.repositorys.*;
import com.proyecto.servicios.service.ClienteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class ClienteServiceImpl implements ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private DomicilioRepository domicilioRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ClienteMapper clienteMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public ClienteResponse registrarCliente(RegistroClienteRequest request) {
        // 1. Validar mayoría de edad (18+ años)
        if (request.getFechaNacimiento() == null) {
            throw new ValidacionNegocioException("La fecha de nacimiento es obligatoria");
        }
        int edad = Period.between(request.getFechaNacimiento(), LocalDate.now()).getYears();
        if (edad < 18) {
            throw new ValidacionNegocioException("El cliente debe ser mayor de edad (18 años o más)");
        }

        // 2. Validar unicidad de CURP, RFC y Correo
        String curp = request.getCurp().trim().toUpperCase();
        if (clienteRepository.existsByCurp(curp)) {
            throw new CurpDuplicadaException("Ya existe un cliente registrado con la CURP: " + curp);
        }

        String rfc = request.getRfc().trim().toUpperCase();
        if (clienteRepository.existsByRfc(rfc)) {
            throw new RfcDuplicadoException("Ya existe un cliente registrado con el RFC: " + rfc);
        }

        String correo = request.getCorreo().trim().toLowerCase();
        if (clienteRepository.existsByCorreo(correo) || usuarioRepository.existsByCorreo(correo)) {
            throw new CorreoDuplicadoException("Ya existe un registro asociado al correo electrónico: " + correo);
        }

        // 3. Crear Domicilio
        Domicilio domicilio = clienteMapper.toDomicilioEntity(request.getDomicilio());
        Domicilio domicilioGuardado = domicilioRepository.save(domicilio);

        // 4. Crear Cliente
        Cliente cliente = clienteMapper.toClienteEntity(request, domicilioGuardado);
        Cliente clienteGuardado = clienteRepository.save(cliente);

        // 5. Crear Cuenta Bancaria Única
        String numeroCuenta = generarNumeroCuentaUnico();
        Cuenta cuenta = Cuenta.builder()
                .numeroCuenta(numeroCuenta)
                .cliente(clienteGuardado)
                .saldo(BigDecimal.ZERO)
                .estatus(EstatusCuenta.ACTIVA)
                .build();
        cuentaRepository.save(cuenta);

        // 6. Crear Usuario de Acceso cifrado con BCrypt
        String passwordCifrada = passwordEncoder.encode(request.getPassword());
        Usuario usuario = Usuario.builder()
                .cliente(clienteGuardado)
                .correo(correo)
                .password(passwordCifrada)
                .activo(true)
                .build();
        usuarioRepository.save(usuario);

        // Refrescar cliente completo con sus relaciones
        Cliente clienteCompleto = clienteRepository.findById(clienteGuardado.getId())
                .orElse(clienteGuardado);

        return clienteMapper.toClienteResponse(clienteCompleto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClienteResponse> obtenerTodosLosClientes() {
        return clienteRepository.findAll().stream()
                .map(clienteMapper::toClienteResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClienteResponse> obtenerClientesActivos() {
        return clienteRepository.findByActivo(true).stream()
                .map(clienteMapper::toClienteResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponse obtenerClientePorId(Long id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ClienteNoEncontradoException("No se encontró el cliente con ID: " + id));
        return clienteMapper.toClienteResponse(cliente);
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponse obtenerClientePorCurp(String curp) {
        Cliente cliente = clienteRepository.findByCurp(curp.trim().toUpperCase())
                .orElseThrow(() -> new ClienteNoEncontradoException("No se encontró el cliente con la CURP: " + curp));
        return clienteMapper.toClienteResponse(cliente);
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponse obtenerClientePorRfc(String rfc) {
        Cliente cliente = clienteRepository.findByRfc(rfc.trim().toUpperCase())
                .orElseThrow(() -> new ClienteNoEncontradoException("No se encontró el cliente con el RFC: " + rfc));
        return clienteMapper.toClienteResponse(cliente);
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponse obtenerClientePorCorreo(String correo) {
        Cliente cliente = clienteRepository.findByCorreo(correo.trim().toLowerCase())
                .orElseThrow(() -> new ClienteNoEncontradoException("No se encontró el cliente con el correo: " + correo));
        return clienteMapper.toClienteResponse(cliente);
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponse obtenerClientePorNumeroCuenta(String numeroCuenta) {
        Cliente cliente = clienteRepository.findByNumeroCuenta(numeroCuenta.trim())
                .orElseThrow(() -> new ClienteNoEncontradoException("No se encontró el cliente asociado a la cuenta: " + numeroCuenta));
        return clienteMapper.toClienteResponse(cliente);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClienteResponse> obtenerClientesPorRangoFechas(LocalDate fechaInicio, LocalDate fechaFin) {
        OffsetDateTime start = fechaInicio.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = fechaFin.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);
        return clienteRepository.findClientesByRangoFechas(start, end).stream()
                .map(clienteMapper::toClienteResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClienteResponse> buscarClientesDinamico(
            String curp,
            String rfc,
            String correo,
            String nombre,
            String apellido,
            Boolean activo,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            String numeroCuenta
    ) {
        return clienteRepository.findAll(
                ClienteSpecification.buscarConFiltros(curp, rfc, correo, nombre, apellido, activo, fechaInicio, fechaFin, numeroCuenta)
        ).stream().map(clienteMapper::toClienteResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ClienteResponse actualizarCliente(Long id, ActualizaClienteRequest request) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ClienteNoEncontradoException("No se encontró el cliente con ID: " + id));

        // Validar mayoría de edad
        if (Period.between(request.getFechaNacimiento(), LocalDate.now()).getYears() < 18) {
            throw new ValidacionNegocioException("El cliente debe ser mayor de edad (18 años o más)");
        }

        // Si el correo cambió, verificar que no esté usado por otro cliente/usuario
        String nuevoCorreo = request.getCorreo().trim().toLowerCase();
        if (!cliente.getCorreo().equalsIgnoreCase(nuevoCorreo)) {
            if (clienteRepository.existsByCorreo(nuevoCorreo) || usuarioRepository.existsByCorreo(nuevoCorreo)) {
                throw new CorreoDuplicadoException("El correo " + nuevoCorreo + " ya está en uso por otro registro");
            }
            cliente.setCorreo(nuevoCorreo);
            if (cliente.getUsuario() != null) {
                cliente.getUsuario().setCorreo(nuevoCorreo);
            }
        }

        cliente.setNombre(request.getNombre().trim());
        cliente.setSegundoNombre(request.getSegundoNombre() != null ? request.getSegundoNombre().trim() : null);
        cliente.setApellidoPaterno(request.getApellidoPaterno().trim());
        cliente.setApellidoMaterno(request.getApellidoMaterno().trim());
        cliente.setFechaNacimiento(request.getFechaNacimiento());
        cliente.setSexo(request.getSexo().trim());
        cliente.setNacionalidad(request.getNacionalidad() != null && !request.getNacionalidad().isBlank() ? request.getNacionalidad().trim() : "Mexicana");
        cliente.setEstadoCivil(request.getEstadoCivil().trim());
        cliente.setTelefonoMovil(request.getTelefonoMovil().trim());
        cliente.setTelefonoAlternativo(request.getTelefonoAlternativo() != null ? request.getTelefonoAlternativo().trim() : null);
        cliente.setOcupacion(request.getOcupacion().trim());
        cliente.setEmpresa(request.getEmpresa().trim());
        cliente.setIngresoMensual(request.getIngresoMensual());

        // Actualizar Domicilio
        Domicilio dom = cliente.getDomicilio();
        if (dom != null && request.getDomicilio() != null) {
            dom.setCalle(request.getDomicilio().getCalle().trim());
            dom.setNumeroExterior(request.getDomicilio().getNumeroExterior().trim());
            dom.setNumeroInterior(request.getDomicilio().getNumeroInterior() != null ? request.getDomicilio().getNumeroInterior().trim() : null);
            dom.setColonia(request.getDomicilio().getColonia().trim());
            dom.setMunicipio(request.getDomicilio().getMunicipio().trim());
            dom.setEstado(request.getDomicilio().getEstado().trim());
            dom.setCodigoPostal(request.getDomicilio().getCodigoPostal().trim());
            if (request.getDomicilio().getPais() != null && !request.getDomicilio().getPais().isBlank()) {
                dom.setPais(request.getDomicilio().getPais().trim());
            }
        }

        Cliente clienteActualizado = clienteRepository.save(cliente);
        return clienteMapper.toClienteResponse(clienteActualizado);
    }

    @Override
    @Transactional
    public void darDeBajaLogicaCliente(Long id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ClienteNoEncontradoException("No se encontró el cliente con ID: " + id));

        cliente.setActivo(false);
        clienteRepository.save(cliente);

        if (cliente.getUsuario() != null) {
            cliente.getUsuario().setActivo(false);
            usuarioRepository.save(cliente.getUsuario());
        }

        cuentaRepository.findByClienteId(id).stream()
                .filter(cuenta -> cuenta.getEstatus() == EstatusCuenta.ACTIVA)
                .forEach(cuenta -> cuenta.setEstatus(EstatusCuenta.INACTIVA));
    }

    private String generarNumeroCuentaUnico() {
        Random random = new Random();
        String numeroCuenta;
        do {
            long sufijo = 100000000000L + (long) (random.nextDouble() * 899999999999L);
            numeroCuenta = "4001" + sufijo;
        } while (cuentaRepository.existsByNumeroCuenta(numeroCuenta));
        return numeroCuenta;
    }
}
