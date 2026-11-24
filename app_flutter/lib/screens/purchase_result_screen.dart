import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../state/session.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';

/// Resultado de una compra en uno de los cuatro estados que define el backend.
class ResultadoCompra {
  ResultadoCompra._(
    this.estado,
    this.mensaje, {
    this.pago,
    this.detalle,
    this.idTransaccion,
    this.codigoError,
    this.error,
    this.codigoProveedor,
    this.mensajeProveedor,
    this.sugerencia,
    DateTime? fecha,
  }) : fecha = fecha ?? DateTime.now();

  factory ResultadoCompra.exitosa(ResultadoPago pago) => ResultadoCompra._(
        EstadoCompra.exitosa,
        'Tu compra se realizó correctamente.',
        pago: pago,
        idTransaccion: pago.idTransaccion,
      );

  factory ResultadoCompra.desdeError(ApiException e) {
    final estado = e.sinConexion
        ? EstadoCompra.errorComunicacion
        : e.detalles.containsKey('estadoCompra')
            ? estadoCompraDe(e.detalles['estadoCompra'])
            : (e.status >= 500 ? EstadoCompra.errorComunicacion : EstadoCompra.rechazada);
    String? valor(String clave) {
      final v = e.detalles[clave]?.trim();
      return v == null || v.isEmpty ? null : v;
    }

    // Errores de validación (400) traen el motivo por campo: se muestra el primero como detalle.
    final primerCampo = e.status == 400 && !e.detalles.containsKey('codigo') && e.detalles.isNotEmpty
        ? e.detalles.values.first
        : null;
    return ResultadoCompra._(
      estado,
      e.mensaje,
      detalle: valor('detalle') ?? primerCampo,
      idTransaccion: int.tryParse(e.detalles['idTransaccion'] ?? ''),
      codigoError: valor('codigo') ?? (e.sinConexion ? null : '${e.status}'),
      error: valor('error'),
      codigoProveedor: valor('codigoProveedor'),
      mensajeProveedor: valor('mensajeProveedor'),
      sugerencia: valor('sugerencia'),
    );
  }

  final EstadoCompra estado;
  final String mensaje;
  final ResultadoPago? pago;
  final String? detalle;
  final int? idTransaccion;

  /// Código de error de esta API (por ejemplo 1101) y su nombre (GESTOPAGO_RECHAZADO).
  final String? codigoError;
  final String? error;

  /// Código y mensaje que devolvió GestoPago, cuando el rechazo viene del proveedor.
  final String? codigoProveedor;
  final String? mensajeProveedor;

  /// Qué puede hacer el cliente (lo calcula el servidor para que web y móvil digan lo mismo).
  final String? sugerencia;
  final DateTime fecha;
}

class PurchaseResultScreen extends StatefulWidget {
  const PurchaseResultScreen({
    super.key,
    required this.session,
    required this.producto,
    required this.importe,
    required this.resultado,
    this.referencia,
    this.cuenta,
  });

  final Session session;
  final Producto producto;
  final double importe;
  final ResultadoCompra resultado;

  /// Teléfono o referencia que se intentó pagar, y cuenta enmascarada (para el detalle y para soporte).
  final String? referencia;
  final String? cuenta;

  @override
  State<PurchaseResultScreen> createState() => _PurchaseResultScreenState();
}

class _PurchaseResultScreenState extends State<PurchaseResultScreen> {
  late EstadoCompra _estado = widget.resultado.estado;
  late String _mensaje = widget.resultado.mensaje;
  bool _consultando = false;

  Future<void> _consultarEstado() async {
    final id = widget.resultado.idTransaccion;
    if (id == null) return;
    setState(() => _consultando = true);
    try {
      final (estado, mensaje) = await widget.session.api.estadoCompra(id);
      if (!mounted) return;
      setState(() {
        _estado = estado;
        if (mensaje.isNotEmpty) _mensaje = mensaje;
      });
      if (estado == EstadoCompra.pendiente) {
        mostrarSnack(context, 'La compra sigue en proceso. Consulta de nuevo en un momento.');
      }
    } on ApiException catch (e) {
      if (mounted) mostrarSnack(context, e.mensaje, error: true);
    } finally {
      if (mounted) setState(() => _consultando = false);
    }
  }

  ({Color color, IconData icono, String titulo}) get _estilo => switch (_estado) {
        EstadoCompra.exitosa =>
          (color: Estados.exito, icono: Icons.check_circle_rounded, titulo: 'Compra exitosa'),
        EstadoCompra.rechazada =>
          (color: Estados.rechazo, icono: Icons.cancel_rounded, titulo: 'Compra rechazada'),
        EstadoCompra.pendiente =>
          (color: Estados.pendiente, icono: Icons.schedule_rounded, titulo: 'Compra pendiente'),
        EstadoCompra.errorComunicacion =>
          (color: Estados.error, icono: Icons.cloud_off_rounded, titulo: 'Error de comunicación'),
      };

  String get _explicacion => switch (_estado) {
        EstadoCompra.exitosa => 'GestoPago confirmó la operación. Ya aparece en tu historial.',
        EstadoCompra.rechazada => 'La operación no se realizó y no se hizo ningún cargo a tu cuenta.',
        EstadoCompra.pendiente =>
          'GestoPago aún no confirma el resultado. No vuelvas a pagar: consulta el estado en unos minutos.',
        EstadoCompra.errorComunicacion =>
          'No pudimos comunicarnos con GestoPago. Revisa tu historial antes de intentar de nuevo.',
      };

  String get _cargo => switch (_estado) {
        EstadoCompra.rechazada => 'Ninguno: tu saldo no cambió',
        EstadoCompra.pendiente => 'Retenido hasta que GestoPago confirme',
        EstadoCompra.errorComunicacion => 'Revisa tu historial y tu saldo',
        EstadoCompra.exitosa => dinero(widget.importe),
      };

  /// Resumen en texto plano (sin datos sensibles) para pegarlo en un mensaje a soporte.
  Future<void> _copiarDetalle() async {
    final r = widget.resultado;
    final lineas = [
      'GestoPago - ${_estilo.titulo}',
      'Producto: ${widget.producto.producto} (${widget.producto.idProducto})',
      'Importe: ${dinero(widget.importe)}',
      if (widget.referencia != null && widget.referencia!.isNotEmpty) 'Referencia: ${widget.referencia}',
      if (widget.cuenta != null) 'Cuenta: ${widget.cuenta}',
      'Fecha: ${fechaHora(r.fecha)}',
      if (r.idTransaccion != null) 'Folio: ${r.idTransaccion}',
      if (r.codigoProveedor != null) 'Código GestoPago: ${r.codigoProveedor}',
      if (r.mensajeProveedor != null) 'Mensaje GestoPago: ${r.mensajeProveedor}',
      if (r.codigoError != null) 'Código de error: ${r.codigoError}${r.error != null ? ' (${r.error})' : ''}',
    ];
    await Clipboard.setData(ClipboardData(text: lineas.join('\n')));
    if (mounted) mostrarSnack(context, 'Detalle copiado. Pégalo al escribir a soporte.');
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final e = _estilo;
    final pago = widget.resultado.pago;
    final exitosa = _estado == EstadoCompra.exitosa;

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) Navigator.pop(context, exitosa);
      },
      child: Scaffold(
        appBar: AppBar(
          automaticallyImplyLeading: false,
          actions: [
            IconButton(
              tooltip: 'Cerrar',
              icon: const Icon(Icons.close),
              onPressed: () => Navigator.pop(context, exitosa),
            ),
          ],
        ),
        body: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 32),
          child: Centrado(
            maxWidth: 520,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Semantics(
                  liveRegion: true,
                  child: Column(
                    children: [
                      Container(
                        width: 96,
                        height: 96,
                        decoration: BoxDecoration(
                            color: e.color.withValues(alpha: 0.12), shape: BoxShape.circle),
                        child: Icon(e.icono, color: e.color, size: 64),
                      ),
                      const SizedBox(height: 16),
                      Text(e.titulo,
                          style: t.textTheme.headlineSmall
                              ?.copyWith(fontWeight: FontWeight.w800, color: e.color)),
                      const SizedBox(height: 8),
                      Text(_mensaje, textAlign: TextAlign.center, style: t.textTheme.titleSmall),
                      const SizedBox(height: 6),
                      Text(_explicacion,
                          textAlign: TextAlign.center,
                          style: t.textTheme.bodyMedium
                              ?.copyWith(color: t.colorScheme.onSurfaceVariant)),
                    ],
                  ),
                ),
                const SizedBox(height: 24),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(20),
                    child: Column(
                      children: [
                        Row(
                          children: [
                            LogoServicio(
                                url: widget.producto.logoUrl,
                                nombre: widget.producto.servicio,
                                size: 44),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Text(widget.producto.producto,
                                  style: t.textTheme.titleSmall
                                      ?.copyWith(fontWeight: FontWeight.w700)),
                            ),
                          ],
                        ),
                        const Divider(height: 28),
                        FilaDato('Importe', dinero(pago?.monto ?? widget.importe), destacado: true),
                        if (pago != null) ...[
                          if ((pago.comision ?? 0) > 0) FilaDato('Comisión', dinero(pago.comision)),
                          if (pago.referencia.isNotEmpty) FilaDato('Referencia', pago.referencia),
                          FilaDato('Autorización', pago.numAutorizacion),
                          FilaDato('ID GestoPago', pago.idTxGestoPago),
                          FilaDato('Folio', '${pago.idTransaccion ?? ''}'),
                          if (pago.fecha.isNotEmpty) FilaDato('Fecha', pago.fecha),
                          FilaDato('Saldo restante', dinero(pago.saldoRestante)),
                          if (pago.pinProducto.isNotEmpty)
                            FilaDato('Código de la tarjeta', pago.pinProducto, destacado: true),
                          if (pago.leyenda.isNotEmpty) ...[
                            const Divider(height: 24),
                            Text(pago.leyenda, style: t.textTheme.bodySmall),
                          ],
                        ] else ...[
                          if (widget.referencia != null && widget.referencia!.isNotEmpty)
                            FilaDato(widget.producto.montoLibre ? 'Referencia' : 'Teléfono', widget.referencia!),
                          if (widget.cuenta != null) FilaDato('Cuenta', widget.cuenta!),
                          FilaDato('Fecha y hora', fechaHora(widget.resultado.fecha)),
                          if (widget.resultado.idTransaccion != null)
                            FilaDato('Folio', '${widget.resultado.idTransaccion}'),
                          FilaDato('Cargo a tu cuenta', _cargo),
                        ],
                      ],
                    ),
                  ),
                ),
                if (!exitosa) ...[
                  const SizedBox(height: 16),
                  _DetalleError(resultado: widget.resultado, color: e.color, estado: _estado),
                  const SizedBox(height: 8),
                  Align(
                    alignment: Alignment.centerRight,
                    child: TextButton.icon(
                      onPressed: _copiarDetalle,
                      icon: const Icon(Icons.copy_all_outlined, size: 18),
                      label: const Text('Copiar detalle para soporte'),
                    ),
                  ),
                ],
                const SizedBox(height: 16),
                if (_estado == EstadoCompra.pendiente && widget.resultado.idTransaccion != null) ...[
                  FilledButton.icon(
                    onPressed: _consultando ? null : _consultarEstado,
                    icon: _consultando
                        ? const SizedBox(
                            width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                        : const Icon(Icons.refresh),
                    label: const Text('Consultar estado'),
                  ),
                  const SizedBox(height: 12),
                ],
                exitosa
                    ? FilledButton.icon(
                        onPressed: () => Navigator.pop(context, true),
                        icon: const Icon(Icons.receipt_long),
                        label: const Text('Ver mis compras'),
                      )
                    : OutlinedButton(
                        onPressed: () => Navigator.pop(context, false),
                        child: const Text('Volver al producto'),
                      ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Recuadro con el motivo, los códigos y qué hacer ahora.
class _DetalleError extends StatelessWidget {
  const _DetalleError({required this.resultado, required this.color, required this.estado});

  final ResultadoCompra resultado;
  final Color color;
  final EstadoCompra estado;

  String get _sugerenciaLocal => switch (estado) {
        EstadoCompra.pendiente => 'No vuelvas a pagar: consulta el estado de la compra en unos minutos.',
        EstadoCompra.errorComunicacion => 'Revisa tu conexión y tu historial antes de intentarlo de nuevo.',
        _ => 'Revisa los datos de la compra e inténtalo de nuevo.',
      };

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final r = resultado;
    final motivo = r.mensajeProveedor ?? r.detalle;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Icon(Icons.info_outline, color: color),
                const SizedBox(width: 8),
                Text('Detalle', style: t.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
              ],
            ),
            const SizedBox(height: 8),
            if (motivo != null) FilaDato('Motivo', motivo),
            if (r.codigoProveedor != null) FilaDato('Código GestoPago', r.codigoProveedor!),
            if (r.codigoError != null)
              FilaDato('Código de error', r.error == null ? r.codigoError! : '${r.codigoError} · ${r.error}'),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(Icons.lightbulb_outline, color: color, size: 20),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('¿Qué puedo hacer?', style: t.textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w700)),
                        const SizedBox(height: 4),
                        Text(r.sugerencia ?? _sugerenciaLocal, style: t.textTheme.bodyMedium),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
