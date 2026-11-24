import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../state/session.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';

/// Filtro de estado del historial; [valor] es lo que espera el backend (null = todas).
enum FiltroEstado {
  todas('Todas', null, Icons.list_alt),
  exitosas('Exitosas', 'EXITOSA', Icons.check_circle_outline),
  rechazadas('Rechazadas', 'RECHAZADA', Icons.cancel_outlined),
  pendientes('Pendientes', 'PENDIENTE', Icons.schedule);

  const FiltroEstado(this.texto, this.valor, this.icono);

  final String texto;
  final String? valor;
  final IconData icono;
}

({Color color, IconData icono, String texto}) estiloEstado(EstadoCompra e) => switch (e) {
      EstadoCompra.exitosa => (color: Estados.exito, icono: Icons.check_rounded, texto: 'Exitosa'),
      EstadoCompra.rechazada => (color: Estados.rechazo, icono: Icons.close_rounded, texto: 'Rechazada'),
      EstadoCompra.pendiente => (color: Estados.pendiente, icono: Icons.schedule_rounded, texto: 'Pendiente'),
      EstadoCompra.errorComunicacion => (color: Estados.error, icono: Icons.cloud_off_rounded, texto: 'Sin confirmar'),
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
    return Scaffold(
      appBar: AppBar(
        title: const Text('Mis compras'),
        actions: [
          IconButton(tooltip: 'Actualizar', onPressed: recargar, icon: const Icon(Icons.refresh)),
          const SizedBox(width: 8),
        ],
      ),
      body: Centrado(
        maxWidth: 760,
        child: Column(
          children: [
            _filtros(Theme.of(context)),
            Expanded(child: _lista(Theme.of(context))),
          ],
        ),
      ),
    );
  }

  Widget _filtros(ThemeData t) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
      child: Column(
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
          Wrap(
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: 8,
            children: [
              ActionChip(
                avatar: const Icon(Icons.date_range, size: 18),
                label: Text(_textoRango),
                onPressed: _elegirFechas,
              ),
              if (_rango != null)
                IconButton(
                  tooltip: 'Quitar filtro de fechas',
                  visualDensity: VisualDensity.compact,
                  icon: const Icon(Icons.close, size: 18),
                  onPressed: _quitarFechas,
                ),
            ],
          ),
        ],
      ),
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
        icono: Icons.receipt_long_outlined,
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

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
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
          final e = estiloEstado(c.estadoCompra);
          final exitosa = c.estadoCompra == EstadoCompra.exitosa;
          return SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 32),
            child: Centrado(
              maxWidth: 560,
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Row(
                        children: [
                          LogoServicio(
                            url: c.idServicio != null
                                ? 'https://gestopago.portalventas.net/sistema/images/gestopago/servicios/${c.idServicio}.png'
                                : null,
                            nombre: c.producto,
                            size: 56,
                          ),
                          const SizedBox(width: 14),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(c.producto,
                                    style: t.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
                                const SizedBox(height: 6),
                                Chip(
                                  avatar: Icon(e.icono, color: e.color, size: 18),
                                  label: Text(e.texto),
                                  visualDensity: VisualDensity.compact,
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                      if (!exitosa && c.motivo.isNotEmpty) ...[
                        const SizedBox(height: 12),
                        Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: e.color.withValues(alpha: 0.08),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Icon(Icons.info_outline, color: e.color, size: 20),
                              const SizedBox(width: 10),
                              Expanded(child: Text(c.motivo, style: t.textTheme.bodyMedium)),
                            ],
                          ),
                        ),
                      ],
                      const Divider(height: 32),
                      FilaDato('Importe', dinero(c.monto), destacado: true),
                      if (exitosa && (c.comision ?? 0) > 0) FilaDato('Comisión', dinero(c.comision)),
                      FilaDato('Cargo a tu cuenta', switch (c.estadoCompra) {
                        EstadoCompra.exitosa => dinero(c.monto),
                        EstadoCompra.rechazada => 'Ninguno',
                        EstadoCompra.pendiente => 'Retenido hasta confirmar',
                        EstadoCompra.errorComunicacion => 'Por confirmar',
                      }),
                      FilaDato('Fecha y hora', fechaHora(c.fecha)),
                      FilaDato('Teléfono / referencia', c.referencia),
                      FilaDato('Cuenta', c.cuenta),
                      const Divider(height: 24),
                      FilaDato('Folio', '${c.idTransaccion}'),
                      if (exitosa) ...[
                        FilaDato('Autorización', c.numAutorizacion),
                        FilaDato('ID GestoPago', c.idTxGestoPago),
                      ],
                    ],
                  ),
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}
