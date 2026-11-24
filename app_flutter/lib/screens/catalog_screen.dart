import '../ui/iconos.dart';
import 'package:flutter/material.dart';

import '../api/models.dart';
import '../state/session.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'product_screen.dart';

/// Catálogo guardado del proveedor (GET /productos/almacenados), agrupado por servicio y filtrable por categoría.
class CatalogScreen extends StatefulWidget {
  const CatalogScreen({super.key, required this.session, required this.onCompraExitosa});

  final Session session;
  final VoidCallback onCompraExitosa;

  @override
  State<CatalogScreen> createState() => _CatalogScreenState();
}

class _CatalogScreenState extends State<CatalogScreen> {
  late Future<List<Producto>> _carga = widget.session.api.productos();
  final _busqueda = TextEditingController();
  String _categoria = 'Todas';
  bool _soloDisponibles = true;

  @override
  void dispose() {
    _busqueda.dispose();
    super.dispose();
  }

  void _recargar() => setState(() { _carga = widget.session.api.productos(); });

  /// Agrupa los productos por servicio (un servicio = una marca: Telcel, CFE, Amazon...).
  List<_Servicio> _agrupar(List<Producto> productos) {
    final q = _busqueda.text.trim().toLowerCase();
    final mapa = <int, _Servicio>{};
    for (final p in productos) {
      if (_soloDisponibles && !p.disponibleParaCompra) continue;
      if (_categoria != 'Todas' && p.categoria != _categoria) continue;
      if (q.isNotEmpty &&
          !p.servicio.toLowerCase().contains(q) &&
          !p.producto.toLowerCase().contains(q)) {
        continue;
      }
      mapa.putIfAbsent(p.idServicio, () => _Servicio(p.idServicio, p.servicio, p.logoUrl, p.categoria))
          .productos
          .add(p);
    }
    final lista = mapa.values.toList()
      ..sort((a, b) => a.nombre.toLowerCase().compareTo(b.nombre.toLowerCase()));
    for (final s in lista) {
      s.productos.sort((a, b) => (a.precio ?? 0).compareTo(b.precio ?? 0));
    }
    return lista;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Catálogo'),
        actions: [
          IconButton(tooltip: 'Actualizar', onPressed: _recargar, icon: const Icon(Iconos.refresh)),
          const SizedBox(width: 8),
        ],
      ),
      body: FutureBuilder<List<Producto>>(
        future: _carga,
        builder: (context, snap) {
          if (snap.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError) return Mensaje.error(snap.error!, _recargar);
          final productos = snap.data!;
          if (productos.isEmpty) {
            return Mensaje(
              icono: Iconos.inventory_2_outlined,
              titulo: 'El catálogo está vacío',
              texto: 'Aún no hay productos sincronizados con GestoPago.',
              accion: _recargar,
            );
          }
          final conteo = <String, int>{};
          for (final p in productos) {
            if (_soloDisponibles && !p.disponibleParaCompra) continue;
            conteo[p.categoria] = (conteo[p.categoria] ?? 0) + 1;
          }
          final categorias = conteo.keys.toList()..sort();
          if (_categoria != 'Todas' && !conteo.containsKey(_categoria)) {
            // La categoría elegida ya no tiene productos con el filtro actual: se vuelve a "Todas".
            WidgetsBinding.instance.addPostFrameCallback((_) {
              if (mounted) setState(() => _categoria = 'Todas');
            });
          }
          final servicios = _agrupar(productos);
          final ancho = MediaQuery.sizeOf(context).width >= 900;
          final resultados = SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: Text(
                '${servicios.length} servicio${servicios.length == 1 ? '' : 's'}',
                style: Theme.of(context)
                    .textTheme
                    .bodyMedium
                    ?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant),
              ),
            ),
          );
          final rejilla = servicios.isEmpty
              ? const SliverFillRemaining(
                  hasScrollBody: false,
                  child: Mensaje(
                    icono: Iconos.search_off,
                    titulo: 'Sin resultados',
                    texto: 'Prueba con otro nombre o categoría.',
                  ),
                )
              : SliverPadding(
                  padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
                  sliver: SliverGrid(
                    gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                      maxCrossAxisExtent: 260,
                      mainAxisExtent: 168,
                      crossAxisSpacing: 12,
                      mainAxisSpacing: 12,
                    ),
                    delegate: SliverChildBuilderDelegate(
                      (context, i) => _TarjetaServicio(
                        servicio: servicios[i],
                        onTap: () => _abrirServicio(servicios[i]),
                      ),
                      childCount: servicios.length,
                    ),
                  ),
                );
          final contenido = CustomScrollView(
            slivers: [
              if (!ancho) SliverToBoxAdapter(child: _filtros(categorias, conteo)),
              resultados,
              rejilla,
            ],
          );
          if (!ancho) return Centrado(child: contenido);
          return Centrado(
            maxWidth: 1280,
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SizedBox(
                  width: 280,
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.fromLTRB(24, 4, 8, 24),
                    child: _filtros(categorias, conteo, lateral: true),
                  ),
                ),
                VerticalDivider(width: 1, color: Theme.of(context).colorScheme.outlineVariant),
                Expanded(child: contenido),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _filtros(List<String> categorias, Map<String, int> conteo, {bool lateral = false}) {
    final t = Theme.of(context);
    final total = conteo.values.fold<int>(0, (a, b) => a + b);
    final buscador = SearchBar(
      controller: _busqueda,
      hintText: 'Buscar servicio o producto',
      leading: const Icon(Iconos.search),
      elevation: const WidgetStatePropertyAll(0),
      backgroundColor: WidgetStatePropertyAll(t.colorScheme.surfaceContainerLowest),
      side: WidgetStatePropertyAll(BorderSide(color: t.colorScheme.outlineVariant)),
      trailing: [
        if (_busqueda.text.isNotEmpty)
          IconButton(
            tooltip: 'Limpiar',
            icon: const Icon(Iconos.close),
            onPressed: () => setState(_busqueda.clear),
          ),
      ],
      onChanged: (_) => setState(() {}),
    );
    final disponibles = SwitchListTile(
      contentPadding: EdgeInsets.zero,
      dense: true,
      title: const Text('Solo disponibles'),
      subtitle: const Text('Oculta lo que no se puede comprar'),
      value: _soloDisponibles,
      onChanged: (v) => setState(() => _soloDisponibles = v),
    );
    if (lateral) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          buscador,
          const SizedBox(height: 20),
          Text('CATEGORÍA',
              style: t.textTheme.labelLarge?.copyWith(
                  fontWeight: FontWeight.w700, color: t.colorScheme.onSurfaceVariant, letterSpacing: 0.4)),
          const SizedBox(height: 8),
          for (final c in ['Todas', ...categorias])
            Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: ListTile(
                dense: true,
                selected: _categoria == c,
                selectedTileColor: t.colorScheme.primaryContainer.withValues(alpha: 0.6),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                title: Text(c),
                trailing: Text('${c == 'Todas' ? total : conteo[c]}', style: t.textTheme.labelMedium),
                onTap: () => setState(() => _categoria = c),
              ),
            ),
          const SizedBox(height: 12),
          disponibles,
        ],
      );
    }
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          buscador,
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final c in ['Todas', ...categorias])
                ChoiceChip(
                  label: Text(c == 'Todas' ? 'Todas ($total)' : '$c (${conteo[c]})'),
                  selected: _categoria == c,
                  onSelected: (_) => setState(() => _categoria = c),
                ),
              FilterChip(
                label: const Text('Solo disponibles'),
                selected: _soloDisponibles,
                onSelected: (v) => setState(() => _soloDisponibles = v),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Future<void> _abrirServicio(_Servicio s) async {
    final Producto? elegido;
    if (s.productos.length == 1) {
      elegido = s.productos.first;
    } else {
      elegido = await showModalBottomSheet<Producto>(
        context: context,
        isScrollControlled: true,
        showDragHandle: true,
        constraints: const BoxConstraints(maxWidth: 640),
        builder: (_) => _SelectorProducto(servicio: s),
      );
    }
    if (elegido == null || !mounted) return;
    final exito = await Navigator.of(context).push<bool>(MaterialPageRoute(
      builder: (_) => ProductScreen(session: widget.session, producto: elegido!),
    ));
    if (exito == true) widget.onCompraExitosa();
  }
}

class _Servicio {
  _Servicio(this.id, this.nombre, this.logoUrl, this.categoria);

  final int id;
  final String nombre;
  final String? logoUrl;
  final String categoria;
  final List<Producto> productos = [];

  String get rangoPrecio {
    if (productos.any((p) => p.montoLibre)) return 'Monto libre';
    final precios = productos.map((p) => p.precio).whereType<double>().toList();
    if (precios.isEmpty) return '';
    final min = precios.reduce((a, b) => a < b ? a : b);
    final max = precios.reduce((a, b) => a > b ? a : b);
    return min == max ? dinero(min) : '${dinero(min)} – ${dinero(max)}';
  }
}

class _TarjetaServicio extends StatelessWidget {
  const _TarjetaServicio({required this.servicio, required this.onTap});

  final _Servicio servicio;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final n = servicio.productos.length;
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  LogoServicio(url: servicio.logoUrl, nombre: servicio.nombre, size: 52),
                  const Spacer(),
                  Chip(
                    label: Text(servicio.categoria == 'Pago de servicios' ? 'Servicio' : 'Recarga'),
                    visualDensity: VisualDensity.compact,
                    labelStyle: t.textTheme.labelSmall,
                    padding: EdgeInsets.zero,
                  ),
                ],
              ),
              const Spacer(),
              Text(servicio.nombre,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: t.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(height: 4),
              Text(
                '${servicio.rangoPrecio}${n > 1 ? ' · $n opciones' : ''}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: t.textTheme.bodySmall?.copyWith(color: t.colorScheme.onSurfaceVariant),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SelectorProducto extends StatelessWidget {
  const _SelectorProducto({required this.servicio});

  final _Servicio servicio;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.6,
      maxChildSize: 0.92,
      builder: (context, scroll) => Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
            child: Row(
              children: [
                LogoServicio(url: servicio.logoUrl, nombre: servicio.nombre, size: 44),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(servicio.nombre,
                          style: t.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
                      Text('Elige una opción', style: t.textTheme.bodySmall),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const Divider(height: 1),
          Expanded(
            child: ListView.separated(
              controller: scroll,
              itemCount: servicio.productos.length,
              separatorBuilder: (_, _) => const Divider(height: 1, indent: 20, endIndent: 20),
              itemBuilder: (context, i) {
                final p = servicio.productos[i];
                return ListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 4),
                  title: Text(p.producto),
                  subtitle: p.disponibleParaCompra ? null : const Text('No disponible para compra'),
                  trailing: Text(
                    p.montoLibre ? 'Monto libre' : dinero(p.precio),
                    style: t.textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.w700, color: t.colorScheme.primary),
                  ),
                  onTap: () => Navigator.pop(context, p),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
