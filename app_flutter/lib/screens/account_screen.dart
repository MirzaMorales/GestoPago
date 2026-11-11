import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../legal/documentos_legales.dart';
import '../legal/legal_screen.dart';
import '../state/session.dart';
import '../ui/theme.dart';
import '../ui/filtros_entrada.dart';
import '../ui/widgets.dart';
import 'auth/auth_common.dart';
import 'editar_datos_screen.dart';

/// Datos del usuario, cuentas con saldo, PIN de compras y cierre de sesión.
class AccountScreen extends StatefulWidget {
  const AccountScreen({super.key, required this.session});

  final Session session;

  @override
  State<AccountScreen> createState() => _AccountScreenState();
}

class _AccountScreenState extends State<AccountScreen> {
  Future<Perfil>? _perfil;

  Usuario get u => widget.session.usuario!;

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  void _cargar() {
    if (u.esCliente && u.clienteId != null) {
      setState(() { _perfil = widget.session.api.perfil(u.clienteId!); });
    }
  }

  Future<void> _cerrarSesion() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Cerrar sesión'),
        content: const Text('Podrás volver a entrar desde la web o la app con tus mismas credenciales.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Cerrar sesión')),
        ],
      ),
    );
    if (ok == true) await widget.session.cerrarSesion();
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Mi cuenta')),
      body: RefreshIndicator(
        onRefresh: () async {
          _cargar();
          await _perfil;
        },
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 32),
          children: [
            Centrado(
              maxWidth: 640,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Card(
                    child: ListTile(
                      contentPadding: const EdgeInsets.all(16),
                      leading: CircleAvatar(
                        radius: 26,
                        backgroundColor: t.colorScheme.primaryContainer,
                        child: Text(u.correo.isNotEmpty ? u.correo[0].toUpperCase() : '?',
                            style: TextStyle(
                                fontSize: 22,
                                fontWeight: FontWeight.w700,
                                color: t.colorScheme.onPrimaryContainer)),
                      ),
                      title: FutureBuilder<Perfil>(
                        future: _perfil,
                        builder: (_, s) => Text(
                          s.data?.nombreCompleto.isNotEmpty == true ? s.data!.nombreCompleto : u.correo,
                          style: t.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
                        ),
                      ),
                      subtitle: Text('${u.correo}\n${u.esCliente ? 'Cliente' : 'Administrador'} · '
                          'Sesión en ${widget.session.plataforma == 'WEB' ? 'web' : 'app móvil'}'),
                      isThreeLine: true,
                    ),
                  ),
                  if (u.esCliente) ...[
                    const SizedBox(height: 24),
                    Text('Mis cuentas',
                        style: t.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
                    const SizedBox(height: 8),
                    FutureBuilder<Perfil>(
                      future: _perfil,
                      builder: (context, snap) {
                        if (snap.connectionState != ConnectionState.done) {
                          return const Padding(
                            padding: EdgeInsets.all(24),
                            child: Center(child: CircularProgressIndicator()),
                          );
                        }
                        if (snap.hasError) return Mensaje.error(snap.error!, _cargar);
                        final cuentas = snap.data!.cuentas;
                        if (cuentas.isEmpty) {
                          return const Card(
                            child: Mensaje(
                              icono: Icons.account_balance_wallet_outlined,
                              titulo: 'No tienes cuentas registradas',
                            ),
                          );
                        }
                        return Column(
                          children: [
                            for (final c in cuentas)
                              Padding(
                                padding: const EdgeInsets.only(bottom: 10),
                                child: _TarjetaCuenta(
                                  cuenta: c,
                                  onPin: () => _cambiarPin(c),
                                ),
                              ),
                          ],
                        );
                      },
                    ),
                  ],
                  const SizedBox(height: 24),
                  Card(
                    child: Column(
                      children: [
                        const ListTile(
                          leading: Icon(Icons.shield_outlined),
                          title: Text('Seguridad'),
                          subtitle: Text(
                              'Tu cuenta usa verificación en dos pasos y solo permite una sesión activa a la vez.'),
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        if (u.esCliente && u.clienteId != null)
                          ListTile(
                            leading: const Icon(Icons.edit_note_outlined),
                            title: const Text('Editar mis datos'),
                            subtitle: const Text('Teléfono, empleo y domicilio'),
                            trailing: const Icon(Icons.chevron_right),
                            onTap: _editarDatos,
                          ),
                        ListTile(
                          leading: const Icon(Icons.password_outlined),
                          title: const Text('Cambiar contraseña'),
                          subtitle: const Text('Necesitas tu contraseña actual'),
                          trailing: const Icon(Icons.chevron_right),
                          onTap: _cambiarPassword,
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        ListTile(
                          leading: const Icon(Icons.privacy_tip_outlined),
                          title: const Text('Aviso de Privacidad'),
                          subtitle: const Text('Qué datos usamos, para qué y tus derechos ARCO'),
                          trailing: const Icon(Icons.chevron_right),
                          onTap: () => LegalScreen.abrir(context, avisoPrivacidad),
                        ),
                        ListTile(
                          leading: const Icon(Icons.gavel_outlined),
                          title: const Text('Términos y Condiciones'),
                          subtitle: const Text('Reglas de uso de tu cuenta y de tus compras'),
                          trailing: const Icon(Icons.chevron_right),
                          onTap: () => LegalScreen.abrir(context, terminosCondiciones),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),
                  OutlinedButton.icon(
                    onPressed: _cerrarSesion,
                    icon: const Icon(Icons.logout),
                    label: const Text('Cerrar sesión'),
                    style: OutlinedButton.styleFrom(foregroundColor: t.colorScheme.error),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _editarDatos() async {
    final guardado = await Navigator.of(context).push<bool>(
        MaterialPageRoute(builder: (_) => EditarDatosScreen(session: widget.session)));
    if (guardado == true && mounted) _cargar();
  }

  Future<void> _cambiarPassword() async {
    final cambiada = await showDialog<bool>(
      context: context,
      builder: (_) => _DialogoPassword(session: widget.session),
    );
    if (cambiada == true && mounted) mostrarSnack(context, 'Tu contraseña se actualizó correctamente.');
  }

  Future<void> _cambiarPin(Cuenta c) async {
    final cambiado = await showDialog<bool>(
      context: context,
      builder: (_) => _DialogoPin(session: widget.session, cuenta: c),
    );
    if (cambiado == true && mounted) mostrarSnack(context, 'PIN guardado correctamente.');
  }
}

class _TarjetaCuenta extends StatelessWidget {
  const _TarjetaCuenta({required this.cuenta, required this.onPin});

  final Cuenta cuenta;
  final VoidCallback onPin;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        gradient: LinearGradient(
          colors: [t.colorScheme.primary, t.colorScheme.tertiary],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
      ),
      child: DefaultTextStyle(
        style: TextStyle(color: t.colorScheme.onPrimary),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text('Cuenta ${cuenta.enmascarada}',
                    style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
                const Spacer(),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.2),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(cuenta.activa ? 'Activa' : cuenta.estatus,
                      style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
                ),
              ],
            ),
            const SizedBox(height: 18),
            const Text('Saldo disponible', style: TextStyle(fontSize: 13)),
            Text(dinero(cuenta.saldo),
                style: const TextStyle(fontSize: 30, fontWeight: FontWeight.w800)),
            const SizedBox(height: 12),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton.icon(
                onPressed: onPin,
                style: TextButton.styleFrom(foregroundColor: t.colorScheme.onPrimary),
                icon: const Icon(Icons.pin_outlined),
                label: const Text('Configurar PIN'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DialogoPin extends StatefulWidget {
  const _DialogoPin({required this.session, required this.cuenta});

  final Session session;
  final Cuenta cuenta;

  @override
  State<_DialogoPin> createState() => _DialogoPinState();
}

class _DialogoPinState extends State<_DialogoPin> {
  final _form = GlobalKey<FormState>();
  final _actual = TextEditingController();
  final _nuevo = TextEditingController();
  final _confirma = TextEditingController();
  bool _guardando = false;
  String? _error;

  @override
  void dispose() {
    _actual.dispose();
    _nuevo.dispose();
    _confirma.dispose();
    super.dispose();
  }

  Future<void> _guardar() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _guardando = true;
      _error = null;
    });
    try {
      await widget.session.api.establecerPin(widget.cuenta.numeroCuenta, _nuevo.text,
          pinActual: _actual.text);
      if (mounted) Navigator.pop(context, true);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.mensaje);
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  InputDecoration _deco(String label) =>
      InputDecoration(labelText: label, counterText: '', prefixIcon: const Icon(Icons.pin_outlined));

  String? _validarPin(String? v) => RegExp(r'^\d{4}$').hasMatch(v ?? '') ? null : 'Deben ser 4 dígitos';

  @override
  Widget build(BuildContext context) {
    final formato = [FilteringTextInputFormatter.digitsOnly];
    return AlertDialog(
      title: Text('PIN de la cuenta ${widget.cuenta.enmascarada}'),
      content: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 380),
        child: Form(
          key: _form,
          // Validación en tiempo real: el error aparece al escribir, no hasta enviar.
          autovalidateMode: AutovalidateMode.onUserInteraction,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('El PIN se pide en cada compra. Si ya tienes uno, escríbelo para cambiarlo.'),
              const SizedBox(height: 16),
              TextFormField(
                controller: _actual,
                obscureText: true,
                maxLength: 4,
                keyboardType: TextInputType.number,
                inputFormatters: formato,
                decoration: _deco('PIN actual (si ya tienes)'),
                validator: (v) => (v == null || v.isEmpty) ? null : _validarPin(v),
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _nuevo,
                obscureText: true,
                maxLength: 4,
                keyboardType: TextInputType.number,
                inputFormatters: formato,
                decoration: _deco('PIN nuevo'),
                validator: _validarPin,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _confirma,
                obscureText: true,
                maxLength: 4,
                keyboardType: TextInputType.number,
                inputFormatters: formato,
                decoration: _deco('Confirma el PIN nuevo'),
                validator: (v) => v == _nuevo.text ? null : 'Los PIN no coinciden',
              ),
              if (_error != null) ...[
                const SizedBox(height: 12),
                Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
              ],
            ],
          ),
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancelar')),
        FilledButton(
          onPressed: _guardando ? null : _guardar,
          child: _guardando
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Guardar'),
        ),
      ],
    );
  }
}

/// Cambio de contraseña con la actual (PUT /usuarios/password).
class _DialogoPassword extends StatefulWidget {
  const _DialogoPassword({required this.session});

  final Session session;

  @override
  State<_DialogoPassword> createState() => _DialogoPasswordState();
}

class _DialogoPasswordState extends State<_DialogoPassword> {
  final _form = GlobalKey<FormState>();
  final _actual = TextEditingController();
  final _nueva = TextEditingController();
  final _confirma = TextEditingController();
  bool _ocultar = true;
  bool _guardando = false;
  String? _error;

  @override
  void dispose() {
    _actual.dispose();
    _nueva.dispose();
    _confirma.dispose();
    super.dispose();
  }

  Future<void> _guardar() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _guardando = true;
      _error = null;
    });
    try {
      await widget.session.api.cambiarPassword(widget.session.usuario!.usuarioId, _actual.text, _nueva.text);
      if (mounted) Navigator.pop(context, true);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.detalles.values.isNotEmpty ? e.detalles.values.first : e.mensaje);
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  InputDecoration _deco(String etiqueta, IconData icono) => InputDecoration(
        labelText: etiqueta,
        counterText: '',
        prefixIcon: Icon(icono),
        suffixIcon: IconButton(
          tooltip: _ocultar ? 'Mostrar contraseñas' : 'Ocultar contraseñas',
          icon: Icon(_ocultar ? Icons.visibility_outlined : Icons.visibility_off_outlined),
          onPressed: () => setState(() => _ocultar = !_ocultar),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final formato = [FiltrosEntrada.sinControl];
    return AlertDialog(
      title: const Text('Cambiar contraseña'),
      content: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 420),
        child: Form(
          key: _form,
          autovalidateMode: AutovalidateMode.onUserInteraction,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextFormField(
                  controller: _actual,
                  obscureText: _ocultar,
                  maxLength: FiltrosEntrada.maxPassword,
                  inputFormatters: formato,
                  autofillHints: const [AutofillHints.password],
                  decoration: _deco('Contraseña actual', Icons.lock_outline),
                  validator: (v) => (v ?? '').isEmpty ? 'Escribe tu contraseña actual' : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _nueva,
                  obscureText: _ocultar,
                  maxLength: FiltrosEntrada.maxPassword,
                  inputFormatters: formato,
                  autofillHints: const [AutofillHints.newPassword],
                  decoration: _deco('Contraseña nueva', Icons.lock_reset_outlined),
                  onChanged: (_) => setState(() {}),
                  validator: (v) => validarContrasenaSegura(v) ??
                      (v == _actual.text ? 'Debe ser distinta de la actual' : null),
                ),
                const SizedBox(height: 8),
                ReglasContrasena(valor: _nueva.text),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _confirma,
                  obscureText: _ocultar,
                  maxLength: FiltrosEntrada.maxPassword,
                  inputFormatters: formato,
                  decoration: _deco('Confirma la contraseña nueva', Icons.lock_reset_outlined),
                  validator: (v) => v == _nueva.text ? null : 'Las contraseñas no coinciden',
                ),
                if (_error != null) ...[
                  const SizedBox(height: 12),
                  Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                ],
              ],
            ),
          ),
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancelar')),
        FilledButton(
          onPressed: _guardando ? null : _guardar,
          child: _guardando
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Guardar'),
        ),
      ],
    );
  }
}
