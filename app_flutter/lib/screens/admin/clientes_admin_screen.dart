import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/api_client.dart';
import '../../api/gestopago_api.dart';
import '../../api/models.dart';
import '../../state/session.dart';
import '../../ui/theme.dart';
import '../../ui/widgets.dart';

/// ADMIN: búsqueda de clientes, expediente, abono de saldo y baja.
class ClientesAdminScreen extends StatefulWidget {
  const ClientesAdminScreen({super.key, required this.session});

  final Session session;

  @override
  State<ClientesAdminScreen> createState() => _ClientesAdminScreenState();
}

class _ClientesAdminScreenState extends State<ClientesAdminScreen> {
  final _busqueda = TextEditingController();
  bool _soloActivos = true;
  late Future<List<ClienteDetalle>> _carga = _buscar();

  @override
  void dispose() {
    _busqueda.dispose();
    super.dispose();
  }

  /// Interpreta lo escrito: 16 dígitos = cuenta, CURP, RFC, correo o nombre/apellido.
  Map<String, dynamic> _criterios() {
    final q = _busqueda.text.trim();
    final c = <String, dynamic>{if (_soloActivos) 'activo': true};
    if (q.isEmpty) return c;
    if (RegExp(r'^\d{16}$').hasMatch(q)) return {...c, 'numeroCuenta': q};
    if (RegExp(r'^[A-Za-z]{4}\d{6}[HMhm][A-Za-z]{5}[A-Za-z0-9]\d$').hasMatch(q)) return {...c, 'curp': q.toUpperCase()};
    if (RegExp(r'^[A-Za-z&Ññ]{3,4}\d{6}[A-Za-z0-9]{3}$').hasMatch(q)) return {...c, 'rfc': q.toUpperCase()};
    if (q.contains('@')) return {...c, 'correo': q.toLowerCase()};
    return {...c, 'nombre': q};
  }

  Future<List<ClienteDetalle>> _buscar() => widget.session.api.buscarClientes(_criterios());

  void _recargar() => setState(() { _carga = _buscar(); });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Clientes'),
        actions: [IconButton(tooltip: 'Actualizar', onPressed: _recargar, icon: const Icon(Icons.refresh)), const SizedBox(width: 8)],
      ),
      body: Centrado(
        maxWidth: 900,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
              child: Column(
                children: [
                  TextField(
                    controller: _busqueda,
                    textInputAction: TextInputAction.search,
                    inputFormatters: [
                      FilteringTextInputFormatter.allow(RegExp(r"[\p{L}\p{M}\p{N} .@_%+&'\-]", unicode: true)),
                      LengthLimitingTextInputFormatter(60),
                    ],
                    decoration: InputDecoration(
                      hintText: 'Nombre, correo, CURP, RFC o número de cuenta',
                      prefixIcon: const Icon(Icons.search),
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(28)),
                      suffixIcon: IconButton(tooltip: 'Buscar', icon: const Icon(Icons.arrow_forward), onPressed: _recargar),
                    ),
                    onSubmitted: (_) => _recargar(),
                  ),
                  const SizedBox(height: 8),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: FilterChip(
                      label: const Text('Solo activos'),
                      selected: _soloActivos,
                      onSelected: (v) {
                        _soloActivos = v;
                        _recargar();
                      },
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              child: FutureBuilder<List<ClienteDetalle>>(
                future: _carga,
                builder: (context, snap) {
                  if (snap.connectionState != ConnectionState.done) {
                    return const Center(child: CircularProgressIndicator());
                  }
                  if (snap.hasError) return Mensaje.error(snap.error!, _recargar);
                  final lista = snap.data!;
                  if (lista.isEmpty) {
                    return const Mensaje(icono: Icons.person_search_outlined, titulo: 'Sin resultados',
                        texto: 'Prueba con otro nombre, correo o número de cuenta.');
                  }
                  return ListView.separated(
                    padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
                    itemCount: lista.length,
                    separatorBuilder: (_, _) => const SizedBox(height: 10),
                    itemBuilder: (context, i) => _TarjetaCliente(
                      cliente: lista[i],
                      onTap: () async {
                        await Navigator.of(context).push(MaterialPageRoute(
                            builder: (_) => ClienteAdminDetalleScreen(session: widget.session, clienteId: lista[i].id)));
                        _recargar();
                      },
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TarjetaCliente extends StatelessWidget {
  const _TarjetaCliente({required this.cliente, required this.onTap});

  final ClienteDetalle cliente;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final saldo = cliente.cuentas.fold<double>(0, (s, c) => s + c.saldo);
    return Card(
      clipBehavior: Clip.antiAlias,
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
        onTap: onTap,
        leading: CircleAvatar(
          backgroundColor: cliente.activo ? t.colorScheme.primaryContainer : t.colorScheme.surfaceContainerHighest,
          child: Text(cliente.nombre.isEmpty ? '?' : cliente.nombre[0].toUpperCase()),
        ),
        title: Text(cliente.nombreCompleto, maxLines: 1, overflow: TextOverflow.ellipsis),
        subtitle: Text('${cliente.correo}${cliente.activo ? '' : ' · Inactivo'}', maxLines: 1, overflow: TextOverflow.ellipsis),
        trailing: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text(dinero(saldo), style: t.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
            Text('${cliente.cuentas.length} cuenta${cliente.cuentas.length == 1 ? '' : 's'}', style: t.textTheme.bodySmall),
          ],
        ),
      ),
    );
  }
}

/// Expediente del cliente con sus cuentas: abonar saldo y dar de baja.
class ClienteAdminDetalleScreen extends StatefulWidget {
  const ClienteAdminDetalleScreen({super.key, required this.session, required this.clienteId});

  final Session session;
  final int clienteId;

  @override
  State<ClienteAdminDetalleScreen> createState() => _ClienteAdminDetalleScreenState();
}

class _ClienteAdminDetalleScreenState extends State<ClienteAdminDetalleScreen> {
  late Future<ClienteDetalle> _carga = _cargar();

  Future<ClienteDetalle> _cargar() => widget.session.api.clienteDetalle(widget.clienteId);

  void _recargar() => setState(() { _carga = _cargar(); });

  Future<void> _abonar(ClienteDetalle c, Cuenta cuenta) async {
    final saldo = await showDialog<double>(
      context: context,
      builder: (_) => _DialogoAbono(session: widget.session, cliente: c, cuenta: cuenta),
    );
    if (saldo != null && mounted) {
      mostrarSnack(context, 'Abono aplicado. Saldo nuevo: ${dinero(saldo)}');
      _recargar();
    }
  }

  Future<void> _darDeBaja(ClienteDetalle c) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        icon: Icon(Icons.person_off_outlined, color: Theme.of(ctx).colorScheme.error, size: 36),
        title: const Text('Dar de baja al cliente'),
        content: Text('${c.nombreCompleto} ya no podrá iniciar sesión y sus cuentas quedarán inactivas. '
            'Su historial se conserva. ¿Continuar?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancelar')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Theme.of(ctx).colorScheme.error),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Dar de baja'),
          ),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    try {
      await widget.session.api.darDeBajaCliente(c.id);
      if (!mounted) return;
      mostrarSnack(context, 'Cliente dado de baja.');
      _recargar();
    } on ApiException catch (e) {
      if (mounted) mostrarSnack(context, e.mensaje, error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Expediente del cliente')),
      body: FutureBuilder<ClienteDetalle>(
        future: _carga,
        builder: (context, snap) {
          if (snap.connectionState != ConnectionState.done) return const Center(child: CircularProgressIndicator());
          if (snap.hasError) return Mensaje.error(snap.error!, _recargar);
          final c = snap.data!;
          return ListView(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 32),
            children: [
              Centrado(
                maxWidth: 760,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(20),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(children: [
                              Expanded(
                                child: Text(c.nombreCompleto,
                                    style: t.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
                              ),
                              Chip(
                                avatar: Icon(c.activo ? Icons.check_circle : Icons.block, size: 18,
                                    color: c.activo ? Estados.exito : Estados.rechazo),
                                label: Text(c.activo ? 'Activo' : 'Inactivo'),
                              ),
                            ]),
                            const Divider(height: 24),
                            FilaDato('Correo', c.correo),
                            FilaDato('Teléfono', c.telefonoMovil),
                            FilaDato('CURP', c.curp),
                            FilaDato('RFC', c.rfc),
                            FilaDato('Nacimiento', fechaLarga(c.fechaNacimiento)),
                            FilaDato('Ocupación', '${c.ocupacion} · ${c.empresa}'),
                            FilaDato('Domicilio', c.domicilioTexto),
                            FilaDato('Cliente desde', fechaLarga(c.fechaCreacion)),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 20),
                    Text('Cuentas', style: t.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
                    const SizedBox(height: 8),
                    if (c.cuentas.isEmpty) const Card(child: Mensaje(icono: Icons.account_balance_wallet_outlined, titulo: 'Sin cuentas')),
                    for (final cuenta in c.cuentas)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 10),
                        child: Card(
                          child: ListTile(
                            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                            leading: const Icon(Icons.account_balance_wallet_outlined),
                            title: Text('Cuenta ${cuenta.numeroCuenta}'),
                            subtitle: Text('Saldo ${dinero(cuenta.saldo)} · ${cuenta.estatus}'),
                            trailing: FilledButton.tonalIcon(
                              onPressed: cuenta.activa && c.activo ? () => _abonar(c, cuenta) : null,
                              icon: const Icon(Icons.add_card),
                              label: const Text('Abonar'),
                            ),
                          ),
                        ),
                      ),
                    const SizedBox(height: 24),
                    if (c.activo)
                      OutlinedButton.icon(
                        onPressed: () => _darDeBaja(c),
                        icon: const Icon(Icons.person_off_outlined),
                        label: const Text('Dar de baja al cliente'),
                        style: OutlinedButton.styleFrom(foregroundColor: t.colorScheme.error),
                      ),
                  ],
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

/// Monto del abono con confirmación. Devuelve el saldo nuevo.
class _DialogoAbono extends StatefulWidget {
  const _DialogoAbono({required this.session, required this.cliente, required this.cuenta});

  final Session session;
  final ClienteDetalle cliente;
  final Cuenta cuenta;

  @override
  State<_DialogoAbono> createState() => _DialogoAbonoState();
}

class _DialogoAbonoState extends State<_DialogoAbono> {
  static const maximo = 50000.0;
  final _form = GlobalKey<FormState>();
  final _monto = TextEditingController();
  bool _confirmando = false;
  bool _enviando = false;
  String? _error;

  @override
  void dispose() {
    _monto.dispose();
    super.dispose();
  }

  double get _valor => double.tryParse(_monto.text) ?? 0;

  Future<void> _aplicar() async {
    setState(() {
      _enviando = true;
      _error = null;
    });
    try {
      final saldo = await widget.session.api.abonar(widget.cuenta.numeroCuenta, _valor);
      if (mounted) Navigator.pop(context, saldo);
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _error = e.detalles.values.isNotEmpty ? e.detalles.values.first : e.mensaje;
          _confirmando = false;
        });
      }
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return AlertDialog(
      title: Text(_confirmando ? 'Confirma el abono' : 'Abonar saldo'),
      content: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 400),
        child: _confirmando
            ? Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  FilaDato('Cliente', widget.cliente.nombreCompleto),
                  FilaDato('Cuenta', widget.cuenta.numeroCuenta),
                  FilaDato('Saldo actual', dinero(widget.cuenta.saldo)),
                  FilaDato('Abono', dinero(_valor), destacado: true),
                  FilaDato('Saldo final', dinero(widget.cuenta.saldo + _valor)),
                ],
              )
            : Form(
                key: _form,
                autovalidateMode: AutovalidateMode.onUserInteraction,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text('${widget.cliente.nombreCompleto} · Cuenta ${widget.cuenta.enmascarada}',
                        style: t.textTheme.bodyMedium),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _monto,
                      autofocus: true,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'^\d{0,5}\.?\d{0,2}'))],
                      decoration: InputDecoration(
                        labelText: 'Monto a abonar',
                        prefixText: r'$ ',
                        helperText: 'Máximo ${dinero(maximo)} por operación',
                      ),
                      validator: (v) {
                        final n = double.tryParse(v ?? '');
                        if (n == null || n <= 0) return 'Escribe un monto mayor a cero';
                        if (n > maximo) return 'El máximo por abono es ${dinero(maximo)}';
                        return null;
                      },
                    ),
                    if (_error != null) ...[
                      const SizedBox(height: 12),
                      Text(_error!, style: TextStyle(color: t.colorScheme.error)),
                    ],
                  ],
                ),
              ),
      ),
      actions: [
        TextButton(
          onPressed: _enviando ? null : () => _confirmando ? setState(() => _confirmando = false) : Navigator.pop(context),
          child: Text(_confirmando ? 'Atrás' : 'Cancelar'),
        ),
        FilledButton(
          onPressed: _enviando
              ? null
              : () {
                  if (_confirmando) {
                    _aplicar();
                  } else if (_form.currentState!.validate()) {
                    setState(() => _confirmando = true);
                  }
                },
          child: _enviando
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : Text(_confirmando ? 'Abonar ${dinero(_valor)}' : 'Continuar'),
        ),
      ],
    );
  }
}

