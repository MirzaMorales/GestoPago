import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../state/session.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';

/// Historial de compras exitosas del usuario (POST /pagos/historial), paginado.
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

  @override
  void initState() {
    super.initState();
    recargar();
  }

  Future<void> recargar() async {
    _compras.clear();
    _pagina = 0;
    _hayMas = true;
    await _cargarMas();
  }

  Future<void> _cargarMas() async {
    if (_cargando || !_hayMas) return;
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      final p = await widget.session.api.historial(pagina: _pagina);
      if (!mounted) return;
      setState(() {
        _compras.addAll(p.contenido);
        _hayMas = p.hayMas;
        _pagina++;
        _total = p.totalElementos;
      });
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e);
    } finally {
      if (mounted) setState(() => _cargando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    Widget cuerpo;
    if (_compras.isEmpty && _cargando) {
      cuerpo = const Center(child: CircularProgressIndicator());
    } else if (_compras.isEmpty && _error != null) {
      cuerpo = Mensaje.error(_error!, recargar);
    } else if (_compras.isEmpty) {
      cuerpo = const Mensaje(
        icono: Icons.receipt_long_outlined,
        titulo: 'Aún no tienes compras',
        texto: 'Tus compras exitosas aparecerán aquí.',
      );
    } else {
      cuerpo = RefreshIndicator(
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
                return Text('$_total compra${_total == 1 ? '' : 's'} exitosa${_total == 1 ? '' : 's'}',
                    style: t.textTheme.bodyMedium?.copyWith(color: t.colorScheme.onSurfaceVariant));
              }
              if (i == _compras.length + 1) {
                if (_cargando) {
                  return const Padding(
                      padding: EdgeInsets.all(16), child: Center(child: CircularProgressIndicator()));
                }
                if (_error != null) {
                  return Center(
                      child: TextButton(onPressed: _cargarMas, child: const Text('Reintentar')));
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

    return Scaffold(
      appBar: AppBar(
        title: const Text('Mis compras'),
        actions: [
          IconButton(tooltip: 'Actualizar', onPressed: recargar, icon: const Icon(Icons.refresh)),
          const SizedBox(width: 8),
        ],
      ),
      body: Centrado(maxWidth: 760, child: cuerpo),
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
                decoration: BoxDecoration(
                    color: Estados.exito.withValues(alpha: 0.12), shape: BoxShape.circle),
                child: const Icon(Icons.check_rounded, color: Estados.exito),
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
                    Text('Folio ${c.idTransaccion} · Aut. ${c.numAutorizacion.isEmpty ? '—' : c.numAutorizacion}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: t.textTheme.bodySmall?.copyWith(color: t.colorScheme.onSurfaceVariant)),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Text(dinero(c.monto),
                  style: t.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
            ],
          ),
        ),
      ),
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
                                    style: t.textTheme.titleMedium
                                        ?.copyWith(fontWeight: FontWeight.w700)),
                                const SizedBox(height: 4),
                                Chip(
                                  avatar: const Icon(Icons.check_circle,
                                      color: Estados.exito, size: 18),
                                  label: const Text('Exitosa'),
                                  visualDensity: VisualDensity.compact,
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const Divider(height: 32),
                      FilaDato('Importe', dinero(c.monto), destacado: true),
                      if ((c.comision ?? 0) > 0) FilaDato('Comisión', dinero(c.comision)),
                      FilaDato('Fecha y hora', fechaHora(c.fecha)),
                      FilaDato('Teléfono / referencia', c.referencia),
                      FilaDato('Cuenta', c.cuenta),
                      const Divider(height: 24),
                      FilaDato('Folio', '${c.idTransaccion}'),
                      FilaDato('Autorización', c.numAutorizacion),
                      FilaDato('ID GestoPago', c.idTxGestoPago),
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
