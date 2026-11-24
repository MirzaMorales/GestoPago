package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.model.request.HistorialRequest;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.proyecto.servicios.entity.EstatusTransaccion;
import com.proyecto.servicios.entity.TransaccionPago;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.exception.CompraNoEncontradaException;
import com.proyecto.servicios.exception.ValidacionNegocioException;
import com.proyecto.servicios.model.EstadoCompra;
import com.proyecto.servicios.model.response.CompraResponse;
import com.proyecto.servicios.model.response.EstadoCompraResponse;
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

    /** Límites usados cuando no se filtra por fecha. */
    private static final OffsetDateTime DESDE_SIEMPRE = OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    @Value("${gestopago.tx.zona-horaria:America/Mexico_City}")
    private String zonaHoraria = "America/Mexico_City";

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<CompraResponse> listar(String correoUsuario, HistorialRequest filtros) {
        int pagina = filtros.getPagina() == null ? 0 : filtros.getPagina();
        int tamano = filtros.getTamano() == null ? 20 : filtros.getTamano();
        if (pagina < 0) {
            throw new ValidacionNegocioException("La página no puede ser negativa");
        }
        if (tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new ValidacionNegocioException("El tamaño de página debe estar entre 1 y " + TAMANO_MAXIMO);
        }
        LocalDate inicio = filtros.getFechaInicio();
        LocalDate fin = filtros.getFechaFin();
        if (inicio != null && fin != null && inicio.isAfter(fin)) {
            throw new ValidacionNegocioException("La fecha inicial no puede ser posterior a la final");
        }
        Long clienteId = clienteIdDe(correoUsuario);

        // Los días se interpretan en la zona horaria de la operación (una compra a las 23:00 cuenta en ese día).
        ZoneId zona = ZoneId.of(zonaHoraria);
        OffsetDateTime desde = inicio == null ? DESDE_SIEMPRE : inicio.atStartOfDay(zona).toOffsetDateTime();
        OffsetDateTime hasta = fin == null
                ? OffsetDateTime.now().plusDays(1)
                : fin.plusDays(1).atStartOfDay(zona).toOffsetDateTime();

        Page<TransaccionPago> resultado = transaccionRepository.buscarHistorial(
                clienteId, estatusDe(filtros.getEstado()), desde, hasta,
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
        TransaccionPago t = transaccionRepository.findByIdAndCuentaClienteId(idTransaccion, clienteId)
                .orElseThrow(() -> new CompraNoEncontradaException(idTransaccion));
        Map<Integer, String> nombres = productoRepository.findById(t.getIdProducto())
                .map(p -> Map.of(p.getIdProducto(), p.getProducto()))
                .orElse(Map.of());
        return aCompra(t, nombres);
    }

    @Override
    @Transactional(readOnly = true)
    public EstadoCompraResponse estado(String correoUsuario, Long idTransaccion) {
        Long clienteId = clienteIdDe(correoUsuario);
        TransaccionPago t = transaccionRepository.findByIdAndCuentaClienteId(idTransaccion, clienteId)
                .orElseThrow(() -> new CompraNoEncontradaException(idTransaccion));
        EstadoCompra estado = EstadoCompra.de(t.getEstatus());
        String mensaje = switch (estado) {
            case EXITOSA -> "La compra se realizó correctamente";
            case RECHAZADA -> "La compra no se realizó; el saldo no fue descontado o ya fue devuelto";
            default -> "La compra sigue en proceso; el monto permanece retenido hasta confirmar con GestoPago";
        };
        return EstadoCompraResponse.builder()
                .idTransaccion(t.getId()).estadoCompra(estado).mensaje(mensaje).build();
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
                .estadoCompra(EstadoCompra.de(t.getEstatus()))
                .motivo(motivo(t))
                .build();
    }

    /** Estatus internos que corresponden a cada filtro del cliente; sin filtro, todos. */
    static Set<EstatusTransaccion> estatusDe(String estado) {
        if (estado == null || estado.isBlank()) {
            return EnumSet.allOf(EstatusTransaccion.class);
        }
        return switch (estado) {
            case "EXITOSA" -> EnumSet.of(EstatusTransaccion.EXITOSA);
            case "RECHAZADA" -> EnumSet.of(EstatusTransaccion.FALLIDA);
            case "PENDIENTE" -> EnumSet.of(EstatusTransaccion.PENDIENTE, EstatusTransaccion.EN_REVISION);
            default -> throw new ValidacionNegocioException("El estado debe ser EXITOSA, RECHAZADA o PENDIENTE");
        };
    }

    private static final Pattern CODIGO_PROVEEDOR = Pattern.compile("código (\\d{1,4})");

    /** Explicación para el cliente; el detalle interno solo se usa para extraer el código de GestoPago. */
    static String motivo(TransaccionPago t) {
        return switch (t.getEstatus()) {
            case EXITOSA -> null;
            case PENDIENTE, EN_REVISION -> "GestoPago aún no confirma el resultado; el importe está retenido hasta confirmarlo";
            case FALLIDA -> {
                Matcher m = CODIGO_PROVEEDOR.matcher(t.getDetalle() == null ? "" : t.getDetalle());
                yield m.find()
                        ? "GestoPago rechazó la compra (código " + m.group(1) + "). No se hizo ningún cargo"
                        : "La compra no se realizó. No se hizo ningún cargo";
            }
        };
    }

    static String enmascarar(String numeroCuenta) {
        if (numeroCuenta == null || numeroCuenta.length() <= 4) {
            return numeroCuenta;
        }
        return "*".repeat(numeroCuenta.length() - 4) + numeroCuenta.substring(numeroCuenta.length() - 4);
    }
}
