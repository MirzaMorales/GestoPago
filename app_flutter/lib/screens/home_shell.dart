import '../ui/iconos.dart';
import '../ui/logo_pagaki.dart';
import 'package:flutter/material.dart';

import '../state/session.dart';
import 'account_screen.dart';
import 'admin/clientes_admin_screen.dart';
import 'admin/pendientes_admin_screen.dart';
import 'catalog_screen.dart';
import 'history_screen.dart';

/// Estructura principal: barra inferior en teléfonos y riel lateral en pantallas anchas (web / tablet).
class HomeShell extends StatefulWidget {
  const HomeShell({super.key, required this.session});

  final Session session;

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int _indice = 0;
  final _historialKey = GlobalKey<HistoryScreenState>();

  late final bool _cliente = widget.session.usuario?.esCliente ?? false;

  late final List<({IconData icono, IconData activo, String texto, Widget vista})> _secciones = [
    if (!_cliente) ...[
      (
        icono: Iconos.people_outline,
        activo: Iconos.people,
        texto: 'Clientes',
        vista: ClientesAdminScreen(session: widget.session),
      ),
      (
        icono: Iconos.pending_actions_outlined,
        activo: Iconos.pending_actions,
        texto: 'Pendientes',
        vista: PendientesAdminScreen(session: widget.session),
      ),
    ],
    (
      icono: Iconos.storefront_outlined,
      activo: Iconos.storefront,
      texto: 'Catálogo',
      vista: CatalogScreen(session: widget.session, onCompraExitosa: _irAHistorial),
    ),
    if (_cliente)
      (
        icono: Iconos.receipt_long_outlined,
        activo: Iconos.receipt_long,
        texto: 'Mis compras',
        vista: HistoryScreen(key: _historialKey, session: widget.session),
      ),
    (
      icono: Iconos.person_outline,
      activo: Iconos.person,
      texto: 'Mi cuenta',
      vista: AccountScreen(session: widget.session),
    ),
  ];

  void _irAHistorial() {
    if (!_cliente) return;
    setState(() => _indice = 1);
    _historialKey.currentState?.recargar();
  }

  void _seleccionar(int i) {
    setState(() => _indice = i);
    if (_cliente && i == 1) _historialKey.currentState?.recargar();
  }

  @override
  Widget build(BuildContext context) {
    final ancho = MediaQuery.sizeOf(context).width >= 800;
    final cuerpo = IndexedStack(index: _indice, children: [for (final s in _secciones) s.vista]);

    if (!ancho) {
      return Scaffold(
        body: cuerpo,
        bottomNavigationBar: NavigationBar(
          selectedIndex: _indice,
          onDestinationSelected: _seleccionar,
          destinations: [
            for (final s in _secciones)
              NavigationDestination(icon: Icon(s.icono), selectedIcon: Icon(s.activo), label: s.texto),
          ],
        ),
      );
    }

    final t = Theme.of(context);
    return Scaffold(
      body: Row(
        children: [
          NavigationRail(
            selectedIndex: _indice,
            onDestinationSelected: _seleccionar,
            labelType: NavigationRailLabelType.all,
            backgroundColor: t.colorScheme.surfaceContainerLow,
            leading: Padding(
              padding: const EdgeInsets.symmetric(vertical: 20),
              child: Column(
                children: [
                  const LogoPagaki(tamano: 48),
                  const SizedBox(height: 6),
                  Text(nombreApp, style: t.textTheme.labelMedium?.copyWith(fontWeight: FontWeight.w800)),
                  Text(lemaApp, style: t.textTheme.labelSmall?.copyWith(color: t.colorScheme.onSurfaceVariant)),
                ],
              ),
            ),
            destinations: [
              for (final s in _secciones)
                NavigationRailDestination(
                    icon: Icon(s.icono), selectedIcon: Icon(s.activo), label: Text(s.texto)),
            ],
          ),
          const VerticalDivider(width: 1),
          Expanded(child: cuerpo),
        ],
      ),
    );
  }
}
