import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../api/gestopago_api.dart';
import '../../state/session.dart';
import '../../ui/filtros_entrada.dart';
import '../../ui/theme.dart';
import '../../ui/widgets.dart';

/// ADMIN: compras cuyo resultado GestoPago no confirmó. Se pueden reconsultar (confirmTx) o resolver a mano.
class PendientesAdminScreen extends StatefulWidget {
  const PendientesAdminScreen({super.key, required this.session});

  final Session session;

  @override
  State<PendientesAdminScreen> createState() => PendientesAdminScreenState();
}

class PendientesAdminScreenState extends State<PendientesAdminScreen> {
  late Future<List<CompraPendiente>> _carga = widget.session.api.comprasPendientes();
  bool _conciliando = false;

  void recargar() => setState(() { _carga = widget.session.api.comprasPendientes(); });

  Future<void> _conciliar() async {
    setState(() => _conciliando = true);
    try {
      final n = await widget.session.api.ejecutarConciliacion();
      if (!mounted) return;
      mostrarSnack(context, n == 0
          ? 'No había compras listas para consultar (GestoPago pide esperar al menos 62 s).'
          : 'Se consultaron $n compra${n == 1 ? '' : 's'} con GestoPago.');
      recargar();
    } on ApiException catch (e) {
      if (mounted) mostrarSnack(context, e.mensaje, error: true);
    } finally {
      if (mounted) setState(() => _conciliando = false);
    }
  }

  Future<void> _resolver(CompraPendiente p, String accion) async {
    final resultado = await showDialog<String>(
      context: context,
      builder: (_) => _DialogoResolver(session: widget.session, pendiente: p, accion: accion),
    );
    if (resultado != null && mounted) {
      mostrarSnack(context, accion == 'CONFIRMAR' ? 'Compra confirmada como exitosa.' : 'Compra revertida: el saldo se devolvió.');
      recargar();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Compras pendientes'),
        actions: [IconButton(tooltip: 'Actualizar', onPressed: recargar, icon: const Icon(Icons.refresh)), const SizedBox(width: 8)],
      ),
      body: Centrado(
        maxWidth: 900,
        child: FutureBuilder<List<CompraPendiente>>(
          future: _carga,
          builder: (context, snap) {
            if (snap.connectionState != ConnectionState.done) return const Center(child: CircularProgressIndicator());
            if (snap.hasError) return Mensaje.error(snap.error!, recargar);
            final lista = snap.data!;
            return ListView(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
              children: [
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Row(
                      children: [
                        const Icon(Icons.sync_alt),
                        const SizedBox(width: 12),
                        const Expanded(
                          child: Text('Cuando GestoPago no responde a tiempo, la compra queda pendiente. '
                              'El sistema la consulta solo cada 2 minutos; también puedes hacerlo ahora.'),
                        ),
                        const SizedBox(width: 12),
                        FilledButton.icon(
                          onPressed: _conciliando ? null : _conciliar,
                          icon: _conciliando
                              ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                              : const Icon(Icons.cloud_sync_outlined),
                          label: const Text('Consultar ahora'),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                if (lista.isEmpty)
                  const Mensaje(
                    icono: Icons.task_alt,
                    titulo: 'No hay compras pendientes',
                    texto: 'Todas las compras tienen un resultado confirmado.',
                  )
                else
                  for (final p in lista)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: _TarjetaPendiente(
                        pendiente: p,
                        onConfirmar: () => _resolver(p, 'CONFIRMAR'),
                        onRevertir: () => _resolver(p, 'REVERTIR'),
                      ),
                    ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _TarjetaPendiente extends StatelessWidget {
  const _TarjetaPendiente({required this.pendiente, required this.onConfirmar, required this.onRevertir});

  final CompraPendiente pendiente;
  final VoidCallback onConfirmar;
  final VoidCallback onRevertir;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final p = pendiente;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                const Icon(Icons.schedule, color: Estados.pendiente),
                const SizedBox(width: 8),
                Expanded(
                  child: Text('Folio ${p.id} · ${dinero(p.monto)}',
                      style: t.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
                ),
                Chip(label: Text(p.estatus), visualDensity: VisualDensity.compact),
              ],
            ),
            const SizedBox(height: 4),
            FilaDato('Cuenta', p.cuentaEnmascarada),
            FilaDato('Producto / servicio', '${p.idProducto ?? '—'} / ${p.idServicio ?? '—'}'),
            FilaDato('Enviada', fechaHora(p.fechaEnvio)),
            FilaDato('UPC', p.upc),
            if (p.detalle.isNotEmpty) FilaDato('Detalle', p.detalle),
            if (p.requiereResolucionManual) ...[
              const SizedBox(height: 8),
              Text('GestoPago no pudo confirmarla. Revisa con el proveedor y resuélvela:',
                  style: t.textTheme.bodySmall?.copyWith(color: t.colorScheme.onSurfaceVariant)),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                alignment: WrapAlignment.end,
                children: [
                  OutlinedButton.icon(
                    onPressed: onRevertir,
                    icon: const Icon(Icons.undo),
                    label: const Text('Revertir y devolver saldo'),
                  ),
                  FilledButton.icon(
                    onPressed: onConfirmar,
                    icon: const Icon(Icons.check),
                    label: const Text('Confirmar como exitosa'),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _DialogoResolver extends StatefulWidget {
  const _DialogoResolver({required this.session, required this.pendiente, required this.accion});

  final Session session;
  final CompraPendiente pendiente;
  final String accion;

  @override
  State<_DialogoResolver> createState() => _DialogoResolverState();
}

class _DialogoResolverState extends State<_DialogoResolver> {
  final _form = GlobalKey<FormState>();
  final _observacion = TextEditingController();
  bool _enviando = false;
  String? _error;

  bool get _confirmar => widget.accion == 'CONFIRMAR';

  @override
  void dispose() {
    _observacion.dispose();
    super.dispose();
  }

  Future<void> _enviar() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _enviando = true;
      _error = null;
    });
    try {
      final r = await widget.session.api
          .resolverPendiente(widget.pendiente.id, widget.accion, _observacion.text.trim());
      if (mounted) Navigator.pop(context, r);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.mensaje);
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return AlertDialog(
      title: Text(_confirmar ? 'Confirmar compra' : 'Revertir compra'),
      content: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 420),
        child: Form(
          key: _form,
          autovalidateMode: AutovalidateMode.onUserInteraction,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(_confirmar
                  ? 'La compra ${widget.pendiente.id} se marcará como exitosa y aparecerá en el historial del cliente.'
                  : 'La compra ${widget.pendiente.id} se cancelará y se devolverán ${dinero(widget.pendiente.monto)} '
                      'a la cuenta del cliente.'),
              const SizedBox(height: 16),
              TextFormField(
                controller: _observacion,
                maxLength: 200,
                maxLines: 2,
                inputFormatters: [FiltrosEntrada.textoLibre],
                decoration: const InputDecoration(
                  labelText: 'Observación',
                  helperText: 'Por ejemplo, el folio que confirmó el proveedor',
                ),
                validator: (v) => (v ?? '').trim().isEmpty ? 'Escribe el motivo de la resolución' : null,
              ),
              if (_error != null) ...[
                const SizedBox(height: 8),
                Text(_error!, style: TextStyle(color: t.colorScheme.error)),
              ],
            ],
          ),
        ),
      ),
      actions: [
        TextButton(onPressed: _enviando ? null : () => Navigator.pop(context), child: const Text('Cancelar')),
        FilledButton(
          style: _confirmar ? null : FilledButton.styleFrom(backgroundColor: t.colorScheme.error),
          onPressed: _enviando ? null : _enviar,
          child: _enviando
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : Text(_confirmar ? 'Confirmar' : 'Revertir'),
        ),
      ],
    );
  }
}
