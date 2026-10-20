package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.Cuenta;
import com.proyecto.servicios.entity.EstatusCuenta;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.exception.CodigoErrorPago;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.request.PagoServicioRequest;
import com.proyecto.servicios.model.response.PagoValidadoResponse;
import com.proyecto.servicios.repositorys.CuentaRepository;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import com.proyecto.servicios.service.PagoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class PagoServiceImpl implements PagoService {

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public PagoValidadoResponse validarPago(PagoServicioRequest request) {
        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(request.getNumeroCuenta())
                .orElseThrow(() -> new PagoException(CodigoErrorPago.CUENTA_NO_ENCONTRADA));

        // CuentaActiva
        if (cuenta.getEstatus() != EstatusCuenta.ACTIVA || !Boolean.TRUE.equals(cuenta.getCliente().getActivo())) {
            throw new PagoException(CodigoErrorPago.CUENTA_NO_ACTIVA);
        }

        // PinAutorizacion
        if (cuenta.getPinAutorizacion() == null) {
            throw new PagoException(CodigoErrorPago.PIN_NO_CONFIGURADO);
        }
        if (!passwordEncoder.matches(request.getPin(), cuenta.getPinAutorizacion())) {
            throw new PagoException(CodigoErrorPago.PIN_INCORRECTO);
        }

        // Producto / servicio / tipoFront contra el catálogo de GestoPago
        ProductoEntity producto = productoRepository.findById(request.getIdProducto())
                .orElseThrow(() -> new PagoException(CodigoErrorPago.PRODUCTO_NO_ENCONTRADO));
        if (!Objects.equals(producto.getIdServicio(), request.getIdServicio())) {
            throw new PagoException(CodigoErrorPago.SERVICIO_NO_COINCIDE);
        }
        if (!Objects.equals(producto.getTipoFront(), request.getTipoFront())) {
            throw new PagoException(CodigoErrorPago.TIPO_FRONT_NO_COINCIDE);
        }

        // saldoCuenta
        if (cuenta.getSaldo().compareTo(request.getMonto()) < 0) {
            throw new PagoException(CodigoErrorPago.SALDO_INSUFICIENTE);
        }

        return PagoValidadoResponse.builder()
                .numeroCuenta(cuenta.getNumeroCuenta())
                .monto(request.getMonto())
                .saldoDisponible(cuenta.getSaldo())
                .idServicio(producto.getIdServicio())
                .idProducto(producto.getIdProducto())
                .producto(producto.getProducto())
                .tipoFront(producto.getTipoFront())
                .build();
    }
}
