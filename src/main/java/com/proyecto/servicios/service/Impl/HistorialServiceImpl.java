package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.EstatusTransaccion;
import com.proyecto.servicios.entity.TransaccionPago;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.exception.CompraNoEncontradaException;
import com.proyecto.servicios.exception.ValidacionNegocioException;
import com.proyecto.servicios.model.response.CompraResponse;
import com.proyecto.servicios.model.response.PaginaResponse;
import com.proyecto.servicios.repositorys.TransaccionPagoRepository;
import com.proyecto.servicios.repositorys.UsuarioRepository;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import com.proyecto.servicios.service.HistorialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HistorialServiceImpl implements HistorialService {

    static final int TAMANO_MAXIMO = 50;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TransaccionPagoRepository transaccionRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<CompraResponse> listar(String correoUsuario, int pagina, int tamano) {
        if (pagina < 0) {
            throw new ValidacionNegocioException("La página no puede ser negativa");
        }
        if (tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new ValidacionNegocioException("El tamaño de página debe estar entre 1 y " + TAMANO_MAXIMO);
        }
        Long clienteId = clienteIdDe(correoUsuario);

        Page<TransaccionPago> resultado = transaccionRepository.findByCuentaClienteIdAndEstatus(
                clienteId, EstatusTransaccion.EXITOSA,
                PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "fechaCreacion", "id")));

        List<Integer> idsProducto = resultado.getContent().stream()
                .map(TransaccionPago::getIdProducto).distinct().toList();
        Map<Integer, String> nombres = productoRepository.findAllById(idsProducto).stream()
                .collect(Collectors.toMap(ProductoEntity::getIdProducto, ProductoEntity::getProducto,
                        (a, b) -> a));

        return PaginaResponse.<CompraResponse>builder()
                .contenido(resultado.getContent().stream().map(t -> aCompra(t, nombres)).toList())
                .pagina(resultado.getNumber())
                .tamano(resultado.getSize())
                .totalElementos(resultado.getTotalElements())
                .totalPaginas(resultado.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CompraResponse detalle(String correoUsuario, Long idTransaccion) {
        Long clienteId = clienteIdDe(correoUsuario);
        TransaccionPago t = transaccionRepository
                .findByIdAndCuentaClienteIdAndEstatus(idTransaccion, clienteId, EstatusTransaccion.EXITOSA)
                .orElseThrow(() -> new CompraNoEncontradaException(idTransaccion));
        Map<Integer, String> nombres = productoRepository.findById(t.getIdProducto())
                .map(p -> Map.of(p.getIdProducto(), p.getProducto()))
                .orElse(Map.of());
        return aCompra(t, nombres);
    }

    private Long clienteIdDe(String correoUsuario) {
        return usuarioRepository.findByCorreo(correoUsuario)
                .filter(u -> u.getCliente() != null)
                .map(u -> u.getCliente().getId())
                .orElseThrow(() -> new ValidacionNegocioException("El usuario no tiene un cliente asociado"));
    }

    private CompraResponse aCompra(TransaccionPago t, Map<Integer, String> nombres) {
        return CompraResponse.builder()
                .idTransaccion(t.getId())
                .fecha(t.getFechaResolucion() != null ? t.getFechaResolucion() : t.getFechaCreacion())
                .idProducto(t.getIdProducto())
                .idServicio(t.getIdServicio())
                .producto(nombres.getOrDefault(t.getIdProducto(), "Producto " + t.getIdProducto()))
                .referencia(t.getTelefonoReferencia())
                .monto(t.getMonto())
                .comision(t.getGpComision())
                .idTxGestoPago(t.getGpIdTx())
                .numAutorizacion(t.getGpNumAutorizacion())
                .cuenta(enmascarar(t.getCuenta().getNumeroCuenta()))
                .estatus(t.getEstatus().name())
                .build();
    }

    static String enmascarar(String numeroCuenta) {
        if (numeroCuenta == null || numeroCuenta.length() <= 4) {
            return numeroCuenta;
        }
        return "*".repeat(numeroCuenta.length() - 4) + numeroCuenta.substring(numeroCuenta.length() - 4);
    }
}
