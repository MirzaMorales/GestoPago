package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.Cuenta;
import com.proyecto.servicios.entity.EstatusCuenta;
import com.proyecto.servicios.entity.EstatusTransaccion;
import com.proyecto.servicios.entity.TransaccionPago;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.exception.CodigoErrorPago;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.gestopago.SendTxResponse;
import com.proyecto.servicios.model.request.PagoServicioRequest;
import com.proyecto.servicios.repositorys.CuentaRepository;
import com.proyecto.servicios.repositorys.TransaccionPagoRepository;
import com.proyecto.servicios.repositorys.gestopago.ProductoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pasos de base de datos del pago, cada uno en su propia transacción corta para no mantener
 * la conexión ni el bloqueo de la cuenta abiertos mientras se espera a GestoPago.
 * Ninguna operación de resolución puede aplicarse dos veces: se bloquea la fila y se revisa el estatus.
 */
@Service
@Slf4j
public class PagoTransaccionService {

    private static final Pattern LLAVE_IDEMPOTENCIA = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    private static final Set<EstatusTransaccion> SIN_RESOLVER = EnumSet.of(EstatusTransaccion.PENDIENTE, EstatusTransaccion.EN_REVISION);

    /** Datos de una operación ya completada, devueltos al repetir la misma Idempotency-Key. */
    public record ResumenPago(BigDecimal monto, BigDecimal saldoRestante, String gpIdTx, String gpNumAutorizacion, BigDecimal gpComision) {
    }

    /** Resultado de retener el saldo. Si {@code repeticion} no es null, la operación ya se había completado y no hay nada que enviar. */
    public record Reserva(Long transaccionId, ProductoEntity producto, String payloadJson, ResumenPago repeticion) {
        public boolean esRepeticion() {
            return repeticion != null;
        }
    }

    public enum ResultadoConciliacion { CONFIRMADA, REVERTIDA, SIN_RESULTADO, OMITIDA }

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private TransaccionPagoRepository transaccionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ReglasMontoPago reglasMonto;

    @Autowired
    private SendTxPayloadBuilder payloadBuilder;

    @Value("${pagos.pin.intentos-maximos:5}")
    private int pinIntentosMaximos;

    @Value("${pagos.pin.bloqueo-minutos:15}")
    private long pinBloqueoMinutos;

    /**
     * Valida (cuenta activa, PIN, idempotencia, producto, monto, saldo), descuenta el monto y registra la
     * transacción PENDIENTE con el payload que se enviará al proveedor. Los rechazos no revierten la
     * transacción (noRollbackFor) para conservar el conteo de intentos fallidos del PIN; ningún rechazo
     * ocurre después de haber modificado el saldo.
     */
    @Transactional(noRollbackFor = PagoException.class)
    public Reserva reservar(PagoServicioRequest request, String idempotencyKey) {
        if (idempotencyKey == null || !LLAVE_IDEMPOTENCIA.matcher(idempotencyKey).matches()) {
            throw new PagoException(CodigoErrorPago.IDEMPOTENCY_KEY_INVALIDA);
        }

        Cuenta cuenta = cuentaRepository.findByNumeroCuentaParaActualizar(request.getNumeroCuenta())
                .orElseThrow(() -> new PagoException(CodigoErrorPago.CUENTA_NO_ENCONTRADA));

        // CuentaActiva
        if (cuenta.getEstatus() != EstatusCuenta.ACTIVA || !Boolean.TRUE.equals(cuenta.getCliente().getActivo())) {
            throw new PagoException(CodigoErrorPago.CUENTA_NO_ACTIVA);
        }

        // PinAutorizacion (con bloqueo temporal por intentos fallidos)
        verificarPin(cuenta, request.getPin());

        // Idempotencia: misma llave => misma operación
        String huella = huella(request);
        var previa = transaccionRepository.findByCuentaIdAndIdempotencyKey(cuenta.getId(), idempotencyKey);
        if (previa.isPresent()) {
            return resolverRepeticion(previa.get(), huella, cuenta);
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

        // Monto y referencia válidos para el tipo de producto
        BigDecimal montoADebitar = reglasMonto.montoADebitar(request, producto);

        // Operación idéntica aún sin resolver (en proceso o en revisión): no se permite una segunda
        if (transaccionRepository.existsByCuentaIdAndIdProductoAndTelefonoReferenciaAndMontoAndEstatusIn(
                cuenta.getId(), producto.getIdProducto(), request.getTelefonoReferencia(), montoADebitar, SIN_RESOLVER)) {
            throw new PagoException(CodigoErrorPago.PAGO_EN_PROCESO);
        }

        // saldoCuenta
        if (cuenta.getSaldo().compareTo(montoADebitar) < 0) {
            throw new PagoException(CodigoErrorPago.SALDO_INSUFICIENTE);
        }

        cuenta.setSaldo(cuenta.getSaldo().subtract(montoADebitar));
        cuentaRepository.save(cuenta);

        String upc = payloadBuilder.generarUpc();
        String payload = payloadBuilder.construir(request, producto, upc);
        OffsetDateTime ahora = OffsetDateTime.now();

        TransaccionPago transaccion = transaccionRepository.save(TransaccionPago.builder()
                .cuenta(cuenta)
                .idProducto(producto.getIdProducto())
                .idServicio(producto.getIdServicio())
                .telefonoReferencia(request.getTelefonoReferencia())
                .monto(montoADebitar)
                .estatus(EstatusTransaccion.PENDIENTE)
                .idempotencyKey(idempotencyKey)
                .huellaSolicitud(huella)
                .upc(upc)
                .payloadEnviado(payload)
                .fechaEnvio(ahora)
                .build());

        return new Reserva(transaccion.getId(), producto, payload, null);
    }

    /** GestoPago aplicó el pago (sendTx 01 o confirmTx 06): se guarda su respuesta. Devuelve el saldo restante. */
    @Transactional
    public BigDecimal confirmar(Long transaccionId, SendTxResponse respuesta) {
        TransaccionPago transaccion = obtenerBloqueada(transaccionId);
        if (transaccion.getEstatus() == EstatusTransaccion.FALLIDA) {
            log.error("Se intentó confirmar la transacción {} que ya estaba revertida", transaccionId);
            throw new PagoException(CodigoErrorPago.TRANSACCION_NO_CONCILIABLE);
        }
        aplicarConfirmada(transaccion, respuesta, null);
        return transaccion.getCuenta().getSaldo();
    }

    /** GestoPago no procesó el pago: se devuelve el monto a la cuenta (una sola vez) y la transacción queda FALLIDA. */
    @Transactional
    public void revertir(Long transaccionId, String detalle) {
        TransaccionPago transaccion = obtenerBloqueada(transaccionId);
        aplicarRevertida(transaccion, detalle);
    }

    /** No se sabe si GestoPago la procesó: el saldo sigue retenido hasta conciliar con confirmTx. */
    @Transactional
    public void marcarEnRevision(Long transaccionId, String detalle) {
        TransaccionPago transaccion = obtenerBloqueada(transaccionId);
        if (transaccion.getEstatus() != EstatusTransaccion.PENDIENTE && transaccion.getEstatus() != EstatusTransaccion.EN_REVISION) {
            return;
        }
        transaccion.setEstatus(EstatusTransaccion.EN_REVISION);
        transaccion.setDetalle(recortar(detalle));
        transaccionRepository.save(transaccion);
    }

    /** Aplica la respuesta de confirmTx: 06 = aplicada, 70 = no aplicada, otro = sin resultado concluyente. */
    @Transactional
    public ResultadoConciliacion aplicarConfirmacion(Long transaccionId, SendTxResponse confirmacion) {
        TransaccionPago transaccion = obtenerBloqueada(transaccionId);
        if (!SIN_RESOLVER.contains(transaccion.getEstatus())) {
            return ResultadoConciliacion.OMITIDA;
        }
        Integer codigo = confirmacion.getCodigo();
        if (Integer.valueOf(SendTxResponse.CODIGO_DUPLICADO_O_APLICADA).equals(codigo)) {
            aplicarConfirmada(transaccion, confirmacion, "Confirmada con confirmTx");
            return ResultadoConciliacion.CONFIRMADA;
        }
        if (Integer.valueOf(SendTxResponse.CODIGO_NO_APLICADA).equals(codigo)) {
            aplicarRevertida(transaccion, "confirmTx: la transacción no fue aplicada");
            return ResultadoConciliacion.REVERTIDA;
        }
        transaccion.setEstatus(EstatusTransaccion.EN_REVISION);
        transaccion.setDetalle(recortar("confirmTx sin resultado concluyente (código " + codigo + ")"));
        transaccionRepository.save(transaccion);
        return ResultadoConciliacion.SIN_RESULTADO;
    }

    /** Resolución manual por un ADMIN cuando confirmTx ya no está disponible (más de 24 h) o no es concluyente. */
    @Transactional
    public ResultadoConciliacion resolverManual(Long transaccionId, boolean confirmar, String observacion, String usuario) {
        TransaccionPago transaccion = obtenerBloqueada(transaccionId);
        if (!SIN_RESOLVER.contains(transaccion.getEstatus())) {
            throw new PagoException(CodigoErrorPago.TRANSACCION_NO_CONCILIABLE);
        }
        String detalle = "Resolución manual por " + usuario + ": " + observacion;
        if (confirmar) {
            transaccion.setEstatus(EstatusTransaccion.EXITOSA);
            transaccion.setDetalle(recortar(detalle));
            transaccion.setFechaResolucion(OffsetDateTime.now());
            transaccionRepository.save(transaccion);
            return ResultadoConciliacion.CONFIRMADA;
        }
        aplicarRevertida(transaccion, detalle);
        return ResultadoConciliacion.REVERTIDA;
    }

    @Transactional(readOnly = true)
    public java.util.List<TransaccionPago> listarSinResolver() {
        var lista = transaccionRepository.findByEstatusInOrderByFechaCreacionAsc(SIN_RESOLVER);
        lista.forEach(t -> t.getCuenta().getNumeroCuenta());
        return lista;
    }

    // ---------- internos ----------

    private void aplicarConfirmada(TransaccionPago transaccion, SendTxResponse respuesta, String detalle) {
        transaccion.setEstatus(EstatusTransaccion.EXITOSA);
        transaccion.setGpIdTx(respuesta.getIdTx());
        transaccion.setGpNumAutorizacion(respuesta.getNumAutorizacion());
        transaccion.setGpComision(respuesta.getComision());
        transaccion.setFechaResolucion(OffsetDateTime.now());
        if (detalle != null) {
            transaccion.setDetalle(recortar(detalle));
        }
        transaccionRepository.save(transaccion);
    }

    /** Devuelve el monto solo si la transacción seguía sin resolver: evita reembolsos dobles. */
    private void aplicarRevertida(TransaccionPago transaccion, String detalle) {
        if (!SIN_RESOLVER.contains(transaccion.getEstatus())) {
            log.warn("Transacción {} ya resuelta ({}); no se revierte de nuevo", transaccion.getId(), transaccion.getEstatus());
            return;
        }
        Cuenta cuenta = cuentaRepository.findByNumeroCuentaParaActualizar(transaccion.getCuenta().getNumeroCuenta())
                .orElseThrow(() -> new PagoException(CodigoErrorPago.CUENTA_NO_ENCONTRADA));
        cuenta.setSaldo(cuenta.getSaldo().add(transaccion.getMonto()));
        cuentaRepository.save(cuenta);

        transaccion.setEstatus(EstatusTransaccion.FALLIDA);
        transaccion.setDetalle(recortar(detalle));
        transaccion.setFechaResolucion(OffsetDateTime.now());
        transaccionRepository.save(transaccion);
    }

    private TransaccionPago obtenerBloqueada(Long id) {
        // Se bloquea primero la cuenta y luego la transacción, el mismo orden que usa reservar()
        TransaccionPago sinBloqueo = transaccionRepository.findById(id)
                .orElseThrow(() -> new PagoException(CodigoErrorPago.TRANSACCION_NO_ENCONTRADA));
        cuentaRepository.findByNumeroCuentaParaActualizar(sinBloqueo.getCuenta().getNumeroCuenta());
        return transaccionRepository.findByIdParaActualizar(id)
                .orElseThrow(() -> new PagoException(CodigoErrorPago.TRANSACCION_NO_ENCONTRADA));
    }

    private void verificarPin(Cuenta cuenta, String pin) {
        if (cuenta.getPinAutorizacion() == null) {
            throw new PagoException(CodigoErrorPago.PIN_NO_CONFIGURADO);
        }
        OffsetDateTime ahora = OffsetDateTime.now();
        if (cuenta.getPinBloqueadoHasta() != null && cuenta.getPinBloqueadoHasta().isAfter(ahora)) {
            throw new PagoException(CodigoErrorPago.PIN_BLOQUEADO);
        }
        if (!passwordEncoder.matches(pin, cuenta.getPinAutorizacion())) {
            int intentos = (cuenta.getPinIntentosFallidos() == null ? 0 : cuenta.getPinIntentosFallidos()) + 1;
            boolean bloquear = intentos >= pinIntentosMaximos;
            cuenta.setPinIntentosFallidos(bloquear ? 0 : intentos);
            if (bloquear) {
                cuenta.setPinBloqueadoHasta(ahora.plusMinutes(pinBloqueoMinutos));
                log.warn("PIN bloqueado temporalmente por intentos fallidos (cuenta id={})", cuenta.getId());
            }
            cuentaRepository.save(cuenta);
            throw new PagoException(bloquear ? CodigoErrorPago.PIN_BLOQUEADO : CodigoErrorPago.PIN_INCORRECTO);
        }
        if ((cuenta.getPinIntentosFallidos() != null && cuenta.getPinIntentosFallidos() > 0) || cuenta.getPinBloqueadoHasta() != null) {
            cuenta.setPinIntentosFallidos(0);
            cuenta.setPinBloqueadoHasta(null);
            cuentaRepository.save(cuenta);
        }
    }

    private Reserva resolverRepeticion(TransaccionPago previa, String huella, Cuenta cuenta) {
        if (!Objects.equals(huella, previa.getHuellaSolicitud())) {
            throw new PagoException(CodigoErrorPago.IDEMPOTENCY_KEY_REUTILIZADA);
        }
        return switch (previa.getEstatus()) {
            case EXITOSA -> new Reserva(previa.getId(),
                    productoRepository.findById(previa.getIdProducto()).orElse(null), null,
                    new ResumenPago(previa.getMonto(), cuenta.getSaldo(), previa.getGpIdTx(),
                            previa.getGpNumAutorizacion(), previa.getGpComision()));
            case FALLIDA -> throw new PagoException(CodigoErrorPago.PAGO_PREVIO_FALLIDO);
            default -> throw new PagoException(CodigoErrorPago.PAGO_EN_PROCESO);
        };
    }

    /** Huella de los datos de la solicitud (sin el PIN) para detectar la reutilización de una llave con otra solicitud. */
    static String huella(PagoServicioRequest r) {
        String base = String.join("|", r.getNumeroCuenta(), String.valueOf(r.getIdProducto()), String.valueOf(r.getIdServicio()),
                String.valueOf(r.getTipoFront()), r.getMonto().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                r.getTelefonoReferencia());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(base.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String recortar(String detalle) {
        if (detalle == null) {
            return null;
        }
        return detalle.length() > 500 ? detalle.substring(0, 500) : detalle;
    }
}
