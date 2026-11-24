import '../ui/iconos.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../state/session.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';

/// Filtro de estado del historial; [valor] es lo que espera el backend (null = todas).
enum FiltroEstado {
  todas('Todas', null, Iconos.list_alt),
  exitosas('Exitosas', 'EXITOSA', Iconos.check_circle_outline),
  rechazadas('Rechazadas', 'RECHAZADA', Iconos.cancel_outlined),
  pendientes('Pendientes', 'PENDIENTE', Iconos.schedule);

  const FiltroEstado(this.texto, this.valor, this.icono);

  final String texto;
  final String? valor;
  final IconData icono;
}

({Color color, IconData icono, String texto}) estiloEstado(EstadoCompra e) => switch (e) {
      EstadoCompra.exitosa => (color: Estados.exito, icono: Iconos.check_rounded, texto: 'Exitosa'),
      EstadoCompra.rechazada => (color: Estados.rechazo, icono: Iconos.close_rounded, texto: 'Rechazada'),
      EstadoCompra.pendiente => (color: Estados.pendiente, icono: Iconos.schedule_rounded, texto: 'Pendiente'),
      EstadoCompra.errorComunicacion => (color: Estados.error, icono: Iconos.cloud_off_rounded, texto: 'Sin confirmar'),
    };

/// Historial de compras del usuario (POST /pagos/historial) con filtros de estado y fechas, paginado.
class HistoryScreen extends StatefulWidget {
  const HistoryScreen({super.key, required this.session});

  final Session session;

  @override
  State<HistoryScreen> createState() => HistoryScreenState();
}

class HistoryScreenState extends State<HistoryScreen> {
  final List<Compra> _compras = [];
  int _pagina = 0;
  bool _hayMas = true;
  bool _cargando = false;
  Object? _error;
  int _total = 0;

  FiltroEstado _filtro = FiltroEstado.todas;
  DateTimeRange? _rango;

  /// Evita que una respuesta tardía de un filtro anterior se mezcle con la del filtro actual.
  int _consulta = 0;

  @override
  void initState() {
    super.initState();
    recargar();
  }

  Future<void> recargar() async {
    _consulta++;
    setState(() {
      _compras.clear();
      _pagina = 0;
      _hayMas = true;
      _cargando = false;
      _error = null;
    });
    await _cargarMas();
  }

  Future<void> _cargarMas() async {
    if (_cargando || !_hayMas) return;
    final consulta = _consulta;
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      final p = await widget.session.api.historial(
        pagina: _pagina,
        estado: _filtro.valor,
        desde: _rango?.start,
        hasta: _rango?.end,
      );
      if (!mounted || consulta != _consulta) return;
      setState(() {
        _compras.addAll(p.contenido);
        _hayMas = p.hayMas;
        _pagina++;
        _total = p.totalElementos;
      });
    } on ApiException catch (e) {
      if (mounted && consulta == _consulta) setState(() => _error = e);
    } finally {
      if (mounted && consulta == _consulta) setState(() => _cargando = false);
    }
  }

  void _cambiarFiltro(FiltroEstado f) {
    if (f == _filtro) return;
    _filtro = f;
    recargar();
  }

  Future<void> _elegirFechas() async {
    final hoy = DateTime.now();
    final rango = await showDateRangePicker(
      context: context,
      firstDate: DateTime(hoy.year - 3),
      lastDate: DateTime(hoy.year, hoy.month, hoy.day),
      initialDateRange: _rango,
      helpText: 'Compras entre',
      saveText: 'Aplicar',
    );
    if (rango == null) return;
    _rango = rango;
    recargar();
  }

  void _quitarFechas() {
    _rango = null;
    recargar();
  }

  String get _textoRango {
    final r = _rango;
    if (r == null) return 'Cualquier fecha';
    final a = fechaLarga(r.start), b = fechaLarga(r.end);
    return a == b ? a : '$a – $b';
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final ancho = MediaQuery.sizeOf(context).width >= 900;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Mis compras'),
        actions: [
          IconButton(tooltip: 'Actualizar', onPressed: recargar, icon: const Icon(Iconos.refresh)),
          const SizedBox(width: 8),
        ],
      ),
      body: ancho
          ? Centrado(
              maxWidth: 1180,
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SizedBox(
                    width: 280,
                    child: SingleChildScrollView(
                      padding: const EdgeInsets.fromLTRB(24, 8, 8, 24),
                      child: _panelFiltros(t),
                    ),
                  ),
                  VerticalDivider(width: 1, color: t.colorScheme.outlineVariant),
                  Expanded(child: _lista(t)),
                ],
              ),
            )
          : Centrado(
              maxWidth: 760,
              child: Column(
                children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
                    child: _filtrosCompactos(t),
                  ),
                  Expanded(child: _lista(t)),
                ],
              ),
            ),
    );
  }

  bool get _hayFiltros => _filtro != FiltroEstado.todas || _rango != null;

  void _limpiarFiltros() {
    _filtro = FiltroEstado.todas;
    _rango = null;
    recargar();
  }

  void _rapido(int dias) {
    final hoy = DateTime.now();
    final fin = DateTime(hoy.year, hoy.month, hoy.day);
    _rango = DateTimeRange(start: fin.subtract(Duration(days: dias - 1)), end: fin);
    recargar();
  }

  void _esteMes() {
    final hoy = DateTime.now();
    _rango = DateTimeRange(start: DateTime(hoy.year, hoy.month), end: DateTime(hoy.year, hoy.month, hoy.day));
    recargar();
  }

  /// Atajos de fecha: el caso habitual es "lo de esta semana o este mes", no escoger dos días en un calendario.
  Widget _atajosFecha() => Wrap(
        spacing: 8,
        runSpacing: 8,
        children: [
          ActionChip(label: const Text('Hoy'), onPressed: () => _rapido(1)),
          ActionChip(label: const Text('7 días'), onPressed: () => _rapido(7)),
          ActionChip(label: const Text('30 días'), onPressed: () => _rapido(30)),
          ActionChip(label: const Text('Este mes'), onPressed: _esteMes),
        ],
      );

  /// Escritorio: panel lateral con estado, fechas y limpiar.
  Widget _panelFiltros(ThemeData t) {
    final titulo = t.textTheme.labelLarge?.copyWith(
        fontWeight: FontWeight.w700, color: t.colorScheme.onSurfaceVariant, letterSpacing: 0.4);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('ESTADO', style: titulo),
        const SizedBox(height: 8),
        for (final f in FiltroEstado.values)
          Padding(
            padding: const EdgeInsets.only(bottom: 4),
            child: ListTile(
              dense: true,
              selected: _filtro == f,
              selectedTileColor: t.colorScheme.primaryContainer.withValues(alpha: 0.6),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              leading: Icon(f.icono, size: 20),
              title: Text(f.texto),
              onTap: () => _cambiarFiltro(f),
            ),
          ),
        const SizedBox(height: 20),
        Text('FECHA', style: titulo),
        const SizedBox(height: 8),
        _atajosFecha(),
        const SizedBox(height: 8),
        OutlinedButton.icon(
          onPressed: _elegirFechas,
          icon: const Icon(Iconos.date_range, size: 18),
          label: Text(_textoRango, overflow: TextOverflow.ellipsis),
        ),
        if (_hayFiltros) ...[
          const SizedBox(height: 16),
          TextButton.icon(
            onPressed: _limpiarFiltros,
            icon: const Icon(Iconos.filter_alt_off_outlined, size: 18),
            label: const Text('Quitar filtros'),
          ),
        ],
      ],
    );
  }

  /// Móvil: chips de estado y una fila de fechas con atajos.
  Widget _filtrosCompactos(ThemeData t) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: [
              for (final f in FiltroEstado.values)
                Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ChoiceChip(
                    avatar: Icon(f.icono, size: 18),
                    label: Text(f.texto),
                    selected: _filtro == f,
                    onSelected: (_) => _cambiarFiltro(f),
                  ),
                ),
            ],
          ),
        ),
        const SizedBox(height: 8),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: [
              InputChip(
                avatar: const Icon(Iconos.date_range, size: 18),
                label: Text(_textoRango),
                onPressed: _elegirFechas,
                onDeleted: _rango == null ? null : _quitarFechas,
              ),
              const SizedBox(width: 8),
              ActionChip(label: const Text('7 días'), onPressed: () => _rapido(7)),
              const SizedBox(width: 8),
              ActionChip(label: const Text('30 días'), onPressed: () => _rapido(30)),
              const SizedBox(width: 8),
              ActionChip(label: const Text('Este mes'), onPressed: _esteMes),
            ],
          ),
        ),
      ],
    );
  }

  Widget _lista(ThemeData t) {
    if (_compras.isEmpty && _cargando) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_compras.isEmpty && _error != null) {
      return Mensaje.error(_error!, recargar);
    }
    if (_compras.isEmpty) {
      final hayFiltros = _filtro != FiltroEstado.todas || _rango != null;
      return Mensaje(
        icono: Iconos.receipt_long_outlined,
        titulo: hayFiltros ? 'No hay compras con estos filtros' : 'Aún no tienes compras',
        texto: hayFiltros ? 'Prueba con otro estado o rango de fechas.' : 'Aquí verás todas tus compras.',
        accion: hayFiltros
            ? () {
                _filtro = FiltroEstado.todas;
                _rango = null;
                recargar();
              }
            : null,
        textoAccion: 'Quitar filtros',
      );
    }
    return RefreshIndicator(
      onRefresh: recargar,
      child: NotificationListener<ScrollNotification>(
        onNotification: (n) {
          if (n.metrics.pixels > n.metrics.maxScrollExtent - 300) _cargarMas();
          return false;
        },
        child: ListView.separated(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
          itemCount: _compras.length + 2,
          separatorBuilder: (_, _) => const SizedBox(height: 10),
          itemBuilder: (context, i) {
            if (i == 0) {
              return Text('$_total compra${_total == 1 ? '' : 's'}',
                  style: t.textTheme.bodyMedium?.copyWith(color: t.colorScheme.onSurfaceVariant));
            }
            if (i == _compras.length + 1) {
              if (_cargando) {
                return const Padding(
                    padding: EdgeInsets.all(16), child: Center(child: CircularProgressIndicator()));
              }
              if (_error != null) {
                return Center(child: TextButton(onPressed: _cargarMas, child: const Text('Reintentar')));
              }
              return const SizedBox.shrink();
            }
            final c = _compras[i - 1];
            return _TarjetaCompra(compra: c, onTap: () => _abrirDetalle(c));
          },
        ),
      ),
    );
  }

  void _abrirDetalle(Compra c) {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => _DetalleCompra(session: widget.session, idTransaccion: c.idTransaccion),
    ));
  }
}

class _TarjetaCompra extends StatelessWidget {
  const _TarjetaCompra({required this.compra, required this.onTap});

  final Compra compra;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final c = compra;
    final e = estiloEstado(c.estadoCompra);
    final exitosa = c.estadoCompra == EstadoCompra.exitosa;
    final segundaLinea = exitosa
        ? 'Folio ${c.idTransaccion} · Aut. ${c.numAutorizacion.isEmpty ? '—' : c.numAutorizacion}'
        : 'Folio ${c.idTransaccion} · ${c.motivo.isEmpty ? e.texto : c.motivo}';
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(color: e.color.withValues(alpha: 0.12), shape: BoxShape.circle),
                child: Icon(e.icono, color: e.color),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(c.producto.isEmpty ? 'Producto ${c.idProducto ?? ''}' : c.producto,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: t.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
                    const SizedBox(height: 2),
                    Text(fechaHora(c.fecha), style: t.textTheme.bodySmall),
                    Text(segundaLinea,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: t.textTheme.bodySmall?.copyWith(color: t.colorScheme.onSurfaceVariant)),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(dinero(c.monto),
                      style: t.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w800,
                        color: exitosa ? null : t.colorScheme.onSurfaceVariant,
                        decoration: c.estadoCompra == EstadoCompra.rechazada ? TextDecoration.lineThrough : null,
                      )),
                  const SizedBox(height: 4),
                  _ChipEstado(estado: c.estadoCompra),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ChipEstado extends StatelessWidget {
  const _ChipEstado({required this.estado});

  final EstadoCompra estado;

  @override
  Widget build(BuildContext context) {
    final e = estiloEstado(estado);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(color: e.color.withValues(alpha: 0.12), borderRadius: BorderRadius.circular(10)),
      child: Text(e.texto,
          style: Theme.of(context).textTheme.labelSmall?.copyWith(color: e.color, fontWeight: FontWeight.w700)),
    );
  }
}

class _DetalleCompra extends StatefulWidget {
  const _DetalleCompra({required this.session, required this.idTransaccion});

  final Session session;
  final int idTransaccion;

  @override
  State<_DetalleCompra> createState() => _DetalleCompraState();
}

class _DetalleCompraState extends State<_DetalleCompra> {
  late Future<Compra> _carga = widget.session.api.detalleCompra(widget.idTransaccion);

  /// Últimos 4 dígitos de la cuenta, con el mismo formato de la tarjeta de Mi cuenta.
  static String _cuentaCorta(String cuenta) {
    final d = cuenta.replaceAll(RegExp(r'[^0-9]'), '');
    return d.length >= 4 ? '•••• ${d.substring(d.length - 4)}' : cuenta;
  }

  String _textoComprobante(Compra c) {
    final e = estiloEstado(c.estadoCompra);
    return [
      'Comprobante GestoPago',
      c.producto,
      'Estado: ${e.texto}',
      'Importe: ${dinero(c.monto)}',
      'Fecha: ${fechaHora(c.fecha)}',
      'Referencia: ${c.referencia}',
      'Folio: ${c.idTransaccion}',
      if (c.numAutorizacion.isNotEmpty) 'Autorización: ${c.numAutorizacion}',
    ].join('\n');
  }

  Future<void> _copiar(String texto, String aviso) async {
    await Clipboard.setData(ClipboardData(text: texto));
    if (mounted) mostrarSnack(context, aviso);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Detalle de la compra')),
      body: FutureBuilder<Compra>(
        future: _carga,
        builder: (context, snap) {
          if (snap.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError) {
            return Mensaje.error(snap.error!, () => setState(() {
                  _carga = widget.session.api.detalleCompra(widget.idTransaccion);
                }));
          }
          final c = snap.data!;
          final ancho = MediaQuery.sizeOf(context).width >= 900;
          final principal = _tarjetaPrincipal(c);
          final lateral = _tarjetaLateral(c);
          return SingleChildScrollView(
            padding: EdgeInsets.fromLTRB(ancho ? 32 : 16, 8, ancho ? 32 : 16, 32),
            child: Centrado(
              maxWidth: ancho ? 1000 : 560,
              child: ancho
                  ? Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(flex: 6, child: principal),
                        const SizedBox(width: 24),
                        Expanded(flex: 4, child: lateral),
                      ],
                    )
                  : Column(children: [principal, const SizedBox(height: 16), lateral]),
            ),
          );
        },
      ),
    );
  }

  /// Encabezado con el importe y todos los datos del comprobante.
  Widget _tarjetaPrincipal(Compra c) {
    final t = Theme.of(context);
    final e = estiloEstado(c.estadoCompra);
    final exitosa = c.estadoCompra == EstadoCompra.exitosa;
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
            color: e.color.withValues(alpha: 0.10),
            padding: const EdgeInsets.all(24),
            child: Row(
              children: [
                LogoServicio(
                  url: c.idServicio != null
                      ? 'https://gestopago.portalventas.net/sistema/images/gestopago/servicios/${c.idServicio}.png'
                      : null,
                  nombre: c.producto,
                  size: 60,
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(c.producto, style: t.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
                      const SizedBox(height: 6),
                      Row(children: [
                        Icon(e.icono, color: e.color, size: 18),
                        const SizedBox(width: 6),
                        Text(e.texto,
                            style: t.textTheme.labelLarge?.copyWith(color: e.color, fontWeight: FontWeight.w700)),
                      ]),
                    ],
                  ),
                ),
                Text(dinero(c.monto),
                    style: t.textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.w800,
                      decoration: c.estadoCompra == EstadoCompra.rechazada ? TextDecoration.lineThrough : null,
                    )),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 12, 24, 20),
            child: Column(
              children: [
                if (!exitosa && c.motivo.isNotEmpty)
                  Container(
                    margin: const EdgeInsets.only(top: 8, bottom: 8),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: e.color.withValues(alpha: 0.08),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(Iconos.info_outline, color: e.color, size: 20),
                        const SizedBox(width: 10),
                        Expanded(child: Text(c.motivo, style: t.textTheme.bodyMedium)),
                      ],
                    ),
                  ),
                if (exitosa && (c.comision ?? 0) > 0) FilaDato('Comisión', dinero(c.comision)),
                FilaDato('Cargo a tu cuenta', switch (c.estadoCompra) {
                  EstadoCompra.exitosa => dinero(c.monto),
                  EstadoCompra.rechazada => 'Ninguno',
                  EstadoCompra.pendiente => 'Retenido hasta confirmar',
                  EstadoCompra.errorComunicacion => 'Por confirmar',
                }),
                FilaDato('Fecha y hora', fechaHora(c.fecha)),
                FilaDato('Teléfono / referencia', c.referencia),
                FilaDato('Cuenta', _cuentaCorta(c.cuenta)),
                const Divider(height: 24),
                FilaDato('Folio', '${c.idTransaccion}'),
                if (exitosa) ...[
                  FilaDato('Autorización', c.numAutorizacion),
                  FilaDato('ID GestoPago', c.idTxGestoPago),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  /// Seguimiento de la compra, qué sigue y acciones sobre el comprobante.
  Widget _tarjetaLateral(Compra c) {
    final t = Theme.of(context);
    final e = estiloEstado(c.estadoCompra);
    final pasos = <(String, String, bool)>[
      ('Compra registrada', fechaHora(c.fecha), true),
      ('Enviada a GestoPago', 'Validación del saldo y del PIN', true),
      switch (c.estadoCompra) {
        EstadoCompra.exitosa => ('Compra confirmada', 'Aut. ${c.numAutorizacion}', true),
        EstadoCompra.rechazada => ('Compra rechazada', 'No se hizo ningún cargo', true),
        EstadoCompra.pendiente => ('Esperando confirmación', 'Puede tardar unos minutos', false),
        EstadoCompra.errorComunicacion => ('Sin confirmar', 'Revisa de nuevo en unos minutos', false),
      },
    ];
    final siguiente = switch (c.estadoCompra) {
      EstadoCompra.exitosa =>
        'Tu recarga o pago ya se aplicó. Guarda el folio y la autorización por si necesitas una aclaración.',
      EstadoCompra.rechazada => 'No se descontó dinero. Revisa la referencia y vuelve a intentarlo desde el catálogo.',
      EstadoCompra.pendiente => 'No pagues otra vez: se resolverá sola y verás el cambio aquí.',
      EstadoCompra.errorComunicacion => 'No pagues otra vez hasta que esta compra cambie de estado.',
    };
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Seguimiento', style: t.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
                const SizedBox(height: 12),
                for (var i = 0; i < pasos.length; i++)
                  IntrinsicHeight(
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Column(
                          children: [
                            Icon(pasos[i].$3 ? Iconos.check_circle : Iconos.schedule,
                                size: 20, color: i == pasos.length - 1 ? e.color : t.colorScheme.primary),
                            if (i < pasos.length - 1)
                              Expanded(child: Container(width: 2, color: t.colorScheme.outlineVariant)),
                          ],
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Padding(
                            padding: const EdgeInsets.only(bottom: 14),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(pasos[i].$1,
                                    style: t.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600)),
                                Text(pasos[i].$2,
                                    style: t.textTheme.bodySmall?.copyWith(color: t.colorScheme.onSurfaceVariant)),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                const Divider(height: 24),
                Text(siguiente, style: t.textTheme.bodyMedium),
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        FilledButton.tonalIcon(
          onPressed: () => _copiar(_textoComprobante(c), 'Comprobante copiado.'),
          icon: const Icon(Iconos.copy_all_outlined),
          label: const Text('Copiar comprobante'),
        ),
        const SizedBox(height: 8),
        OutlinedButton.icon(
          onPressed: () => _copiar('${c.idTransaccion}', 'Folio copiado.'),
          icon: const Icon(Iconos.tag),
          label: const Text('Copiar folio'),
        ),
      ],
    );
  }
}
