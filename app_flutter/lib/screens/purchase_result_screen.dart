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

/// A dónde quiere ir el usuario al salir de la pantalla de resultado.
enum AccionResultado {
  /// Ir al historial de compras.
  verCompras,

  /// Regresar al producto con los datos capturados para corregir y volver a intentar.
  reintentar,

  /// Regresar al catálogo.
  catalogo,
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

  bool get _exitosa => _estado == EstadoCompra.exitosa;

  /// La flecha de volver (y el botón atrás del sistema) regresan al producto si algo falló, o al catálogo si salió bien.
  AccionResultado get _accionVolver => _exitosa ? AccionResultado.catalogo : AccionResultado.reintentar;

  void _salir(AccionResultado accion) => Navigator.pop(context, accion);

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
    final ancho = MediaQuery.sizeOf(context).width >= 900;
    final hero = _Hero(
      estilo: _estilo,
      importe: widget.resultado.pago?.monto ?? widget.importe,
      mensaje: _mensaje,
      explicacion: _explicacion,
      fecha: widget.resultado.fecha,
    );
    final comprobante = _Comprobante(
      producto: widget.producto,
      resultado: widget.resultado,
      referencia: widget.referencia,
      cuenta: widget.cuenta,
      cargo: _cargo,
      estado: _estado,
    );
    final detalleError = _exitosa
        ? const SizedBox.shrink()
        : Column(
            children: [
              const SizedBox(height: 16),
              _DetalleError(resultado: widget.resultado, color: _estilo.color, estado: _estado),
              Align(
                alignment: Alignment.centerRight,
                child: TextButton.icon(
                  onPressed: _copiarDetalle,
                  icon: const Icon(Icons.copy_all_outlined, size: 18),
                  label: const Text('Copiar detalle para soporte'),
                ),
              ),
            ],
          );
    final acciones = _Acciones(
      estado: _estado,
      puedeConsultar: widget.resultado.idTransaccion != null,
      consultando: _consultando,
      onConsultar: _consultarEstado,
      onSalir: _salir,
    );

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _salir(_accionVolver);
      },
      child: Scaffold(
        appBar: AppBar(
          leading: IconButton(
            tooltip: _exitosa ? 'Volver al catálogo' : 'Volver al producto',
            icon: const Icon(Icons.arrow_back),
            onPressed: () => _salir(_accionVolver),
          ),
          title: const Text('Resultado de la compra'),
        ),
        body: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
          child: Centrado(
            maxWidth: 1000,
            child: ancho
                ? Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        flex: 5,
                        child: Column(children: [hero, const SizedBox(height: 20), acciones]),
                      ),
                      const SizedBox(width: 24),
                      Expanded(flex: 6, child: Column(children: [comprobante, detalleError])),
                    ],
                  )
                : Column(children: [hero, const SizedBox(height: 16), comprobante, detalleError]),
          ),
        ),
        // En teléfono, las acciones quedan fijas abajo: siempre se ve cómo seguir.
        bottomNavigationBar: ancho
            ? null
            : SafeArea(
                child: Container(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.surface,
                    border: Border(top: BorderSide(color: Theme.of(context).colorScheme.outlineVariant)),
                  ),
                  child: acciones,
                ),
              ),
      ),
    );
  }
}

/// Resultado de un vistazo: icono, estado, importe y qué significa.
class _Hero extends StatelessWidget {
  const _Hero({
    required this.estilo,
    required this.importe,
    required this.mensaje,
    required this.explicacion,
    required this.fecha,
  });

  final ({Color color, IconData icono, String titulo}) estilo;
  final double importe;
  final String mensaje;
  final String explicacion;
  final DateTime fecha;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Semantics(
      liveRegion: true,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.fromLTRB(24, 28, 24, 26),
        decoration: BoxDecoration(
          color: estilo.color.withValues(alpha: 0.09),
          borderRadius: BorderRadius.circular(24),
          border: Border.all(color: estilo.color.withValues(alpha: 0.25)),
        ),
        child: Column(
          children: [
            Container(
              width: 84,
              height: 84,
              decoration: BoxDecoration(color: estilo.color.withValues(alpha: 0.16), shape: BoxShape.circle),
              child: Icon(estilo.icono, color: estilo.color, size: 56),
            ),
            const SizedBox(height: 16),
            Text(estilo.titulo,
                textAlign: TextAlign.center,
                style: t.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800, color: estilo.color)),
            const SizedBox(height: 6),
            Text(dinero(importe),
                style: t.textTheme.displaySmall?.copyWith(fontWeight: FontWeight.w800, letterSpacing: -0.5)),
            const SizedBox(height: 10),
            Text(mensaje, textAlign: TextAlign.center, style: t.textTheme.titleSmall),
            const SizedBox(height: 6),
            Text(explicacion,
                textAlign: TextAlign.center,
                style: t.textTheme.bodyMedium?.copyWith(color: t.colorScheme.onSurfaceVariant)),
            const SizedBox(height: 12),
            Text(fechaHora(fecha), style: t.textTheme.bodySmall?.copyWith(color: t.colorScheme.onSurfaceVariant)),
          ],
        ),
      ),
    );
  }
}

/// Datos de la compra, como un comprobante.
class _Comprobante extends StatelessWidget {
  const _Comprobante({
    required this.producto,
    required this.resultado,
    required this.referencia,
    required this.cuenta,
    required this.cargo,
    required this.estado,
  });

  final Producto producto;
  final ResultadoCompra resultado;
  final String? referencia;
  final String? cuenta;
  final String cargo;
  final EstadoCompra estado;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final pago = resultado.pago;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Resumen de la compra', style: t.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
            const SizedBox(height: 14),
            Row(
              children: [
                LogoServicio(url: producto.logoUrl, nombre: producto.servicio, size: 44),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(producto.producto,
                      style: t.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
                ),
              ],
            ),
            const Divider(height: 28),
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
              if (referencia != null && referencia!.isNotEmpty)
                FilaDato(producto.montoLibre ? 'Referencia' : 'Teléfono', referencia!),
              if (cuenta != null) FilaDato('Cuenta', cuenta!),
              FilaDato('Fecha y hora', fechaHora(resultado.fecha)),
              if (resultado.idTransaccion != null) FilaDato('Folio', '${resultado.idTransaccion}'),
              FilaDato('Cargo a tu cuenta', cargo),
            ],
          ],
        ),
      ),
    );
  }
}

/// Botones claros según el resultado: lo principal arriba y lo secundario debajo.
/// En teléfono los dos secundarios van lado a lado para ocupar poca altura.
class _Acciones extends StatelessWidget {
  const _Acciones({
    required this.estado,
    required this.puedeConsultar,
    required this.consultando,
    required this.onConsultar,
    required this.onSalir,
  });

  final EstadoCompra estado;
  final bool puedeConsultar;
  final bool consultando;
  final VoidCallback onConsultar;
  final ValueChanged<AccionResultado> onSalir;

  @override
  Widget build(BuildContext context) {
    final compacta = MediaQuery.sizeOf(context).width < 900;

    final verCompras = OutlinedButton.icon(
      onPressed: () => onSalir(AccionResultado.verCompras),
      icon: const Icon(Icons.receipt_long_outlined),
      label: Text(compacta ? 'Mis compras' : 'Ver mis compras'),
    );
    final catalogo = compacta
        ? OutlinedButton.icon(
            onPressed: () => onSalir(AccionResultado.catalogo),
            icon: const Icon(Icons.storefront_outlined),
            label: const Text('Catálogo'),
          )
        : TextButton.icon(
            onPressed: () => onSalir(AccionResultado.catalogo),
            icon: const Icon(Icons.storefront_outlined),
            label: const Text('Ir al catálogo'),
          );

    final Widget? principal;
    final List<Widget> secundarios;
    switch (estado) {
      case EstadoCompra.exitosa:
        principal = FilledButton.icon(
          onPressed: () => onSalir(AccionResultado.verCompras),
          icon: const Icon(Icons.receipt_long),
          label: const Text('Ver mis compras'),
        );
        secundarios = [
          OutlinedButton.icon(
            onPressed: () => onSalir(AccionResultado.catalogo),
            icon: const Icon(Icons.add_shopping_cart),
            label: const Text('Seguir comprando'),
          ),
        ];
      case EstadoCompra.pendiente:
        principal = puedeConsultar
            ? FilledButton.icon(
                onPressed: consultando ? null : onConsultar,
                icon: consultando
                    ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                    : const Icon(Icons.refresh),
                label: const Text('Consultar estado'),
              )
            : null;
        secundarios = [verCompras, catalogo];
      case EstadoCompra.rechazada:
      case EstadoCompra.errorComunicacion:
        principal = FilledButton.icon(
          onPressed: () => onSalir(AccionResultado.reintentar),
          icon: const Icon(Icons.replay),
          label: const Text('Intentar de nuevo'),
        );
        secundarios = [verCompras, catalogo];
    }

    final Widget bloqueSecundario = compacta && secundarios.length == 2
        ? Row(children: [
            Expanded(child: secundarios[0]),
            const SizedBox(width: 8),
            Expanded(child: secundarios[1]),
          ])
        : Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (var i = 0; i < secundarios.length; i++) ...[
                if (i > 0) const SizedBox(height: 8),
                secundarios[i],
              ],
            ],
          );

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (principal != null) ...[principal, const SizedBox(height: 8)],
        bloqueSecundario,
      ],
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
