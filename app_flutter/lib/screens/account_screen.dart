import '../ui/iconos.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../legal/documentos_legales.dart';
import '../legal/legal_screen.dart';
import '../state/session.dart';
import '../ui/tarjeta_cuenta.dart';
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
    final ancho = MediaQuery.sizeOf(context).width >= 900;
    return Scaffold(
      appBar: AppBar(title: const Text('Mi cuenta')),
      body: RefreshIndicator(
        onRefresh: () async {
          _cargar();
          await _perfil;
        },
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsets.fromLTRB(ancho ? 32 : 16, 8, ancho ? 32 : 16, 32),
          children: [
            Centrado(
              maxWidth: 1180,
              child: ancho
                  ? Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(flex: 6, child: _columnaCuentas()),
                        const SizedBox(width: 28),
                        Expanded(flex: 5, child: _columnaAjustes()),
                      ],
                    )
                  : Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [_columnaCuentas(), const SizedBox(height: 24), _columnaAjustes()],
                    ),
            ),
          ],
        ),
      ),
    );
  }

  /// Perfil y tarjetas de las cuentas con su saldo.
  Widget _columnaCuentas() {
    final t = Theme.of(context);
    return Column(
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
                      fontSize: 22, fontWeight: FontWeight.w700, color: t.colorScheme.onPrimaryContainer)),
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
          Text('Mis cuentas', style: t.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
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
                    icono: Iconos.account_balance_wallet_outlined,
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
                        titular: snap.data!.nombreCompleto,
                        onPin: () => _cambiarPin(c),
                      ),
                    ),
                ],
              );
            },
          ),
        ],
      ],
    );
  }

  /// Seguridad, datos personales, documentos legales y cierre de sesión.
  Widget _columnaAjustes() {
    final t = Theme.of(context);
    Widget grupo(String titulo, List<Widget> hijos) => Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.only(left: 4, bottom: 8),
              child: Text(titulo, style: t.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
            ),
            Card(child: Column(children: hijos)),
          ],
        );
    const separador = Divider(height: 1, indent: 16, endIndent: 16);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Card(
          color: t.colorScheme.primaryContainer.withValues(alpha: 0.45),
          child: const ListTile(
            leading: Icon(Iconos.shield_outlined),
            title: Text('Cuenta protegida'),
            subtitle: Text('Verificación en dos pasos y una sola sesión activa a la vez.'),
          ),
        ),
        const SizedBox(height: 24),
        grupo('Seguridad y datos', [
          if (u.esCliente && u.clienteId != null) ...[
            ListTile(
              leading: const Icon(Iconos.edit_note_outlined),
              title: const Text('Editar mis datos'),
              subtitle: const Text('Teléfono, empleo y domicilio'),
              trailing: const Icon(Iconos.chevron_right),
              onTap: _editarDatos,
            ),
            separador,
          ],
          ListTile(
            leading: const Icon(Iconos.password_outlined),
            title: const Text('Cambiar contraseña'),
            subtitle: const Text('Necesitas tu contraseña actual'),
            trailing: const Icon(Iconos.chevron_right),
            onTap: _cambiarPassword,
          ),
        ]),
        const SizedBox(height: 24),
        grupo('Legal', [
          ListTile(
            leading: const Icon(Iconos.privacy_tip_outlined),
            title: const Text('Aviso de Privacidad'),
            subtitle: const Text('Qué datos usamos, para qué y tus derechos ARCO'),
            trailing: const Icon(Iconos.chevron_right),
            onTap: () => LegalScreen.abrir(context, avisoPrivacidad),
          ),
          separador,
          ListTile(
            leading: const Icon(Iconos.gavel_outlined),
            title: const Text('Términos y Condiciones'),
            subtitle: const Text('Reglas de uso de tu cuenta y de tus compras'),
            trailing: const Icon(Iconos.chevron_right),
            onTap: () => LegalScreen.abrir(context, terminosCondiciones),
          ),
        ]),
        const SizedBox(height: 24),
        OutlinedButton.icon(
          onPressed: _cerrarSesion,
          icon: const Icon(Iconos.logout),
          label: const Text('Cerrar sesión'),
          style: OutlinedButton.styleFrom(foregroundColor: t.colorScheme.error),
        ),
      ],
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
    final r = await showDialog<ResultadoPin>(
      context: context,
      builder: (_) => _DialogoPin(session: widget.session, cuenta: c),
    );
    if (r == null || !mounted) return;
    mostrarSnack(
        context,
        r == ResultadoPin.guardado
            ? 'PIN guardado correctamente.'
            : 'Te enviamos un PIN temporal a tu correo. Úsalo y cámbialo cuando quieras.');
    _cargar(); // refresca tienePin
  }
}

/// Tarjeta realista de la cuenta con su acción de PIN debajo.
class _TarjetaCuenta extends StatelessWidget {
  const _TarjetaCuenta({required this.cuenta, required this.titular, required this.onPin});

  final Cuenta cuenta;
  final String titular;
  final VoidCallback onPin;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        TarjetaCuenta(cuenta: cuenta, titular: titular),
        const SizedBox(height: 10),
        Center(
          child: FilledButton.tonalIcon(
            onPressed: onPin,
            icon: const Icon(Iconos.pin_outlined),
            label: Text(cuenta.tienePin ? 'Cambiar PIN de compras' : 'Configurar PIN de compras'),
          ),
        ),
      ],
    );
  }
}

/// Resultado del diálogo del PIN: guardado, o PIN temporal enviado por correo.
enum ResultadoPin { guardado, temporalEnviado }

/// Configurar o cambiar el PIN de compras.
/// Con PIN: PIN actual → código por correo (segundo factor) → PIN nuevo. Sin PIN: se configura directo.
class _DialogoPin extends StatefulWidget {
  const _DialogoPin({required this.session, required this.cuenta});

  final Session session;
  final Cuenta cuenta;

  @override
  State<_DialogoPin> createState() => _DialogoPinState();
}

enum _PasoPin { actual, codigo, nuevo }

class _DialogoPinState extends State<_DialogoPin> {
  final _form = GlobalKey<FormState>();
  final _actual = TextEditingController();
  final _codigo = TextEditingController();
  final _nuevo = TextEditingController();
  final _confirma = TextEditingController();
  late _PasoPin _paso = widget.cuenta.tienePin ? _PasoPin.actual : _PasoPin.nuevo;
  Desafio2fa? _desafio;
  String? _autorizacionId;
  bool _trabajando = false;
  String? _error;

  bool get _conPin => widget.cuenta.tienePin;

  @override
  void dispose() {
    _actual.dispose();
    _codigo.dispose();
    _nuevo.dispose();
    _confirma.dispose();
    super.dispose();
  }

  Future<void> _ejecutar(Future<void> Function() accion) async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _trabajando = true;
      _error = null;
    });
    try {
      await accion();
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.detalles.values.isNotEmpty ? e.detalles.values.first : e.mensaje);
    } finally {
      if (mounted) setState(() => _trabajando = false);
    }
  }

  Future<void> _enviarCodigo() => _ejecutar(() async {
        final d = await widget.session.api.iniciarCambioPin(widget.cuenta.numeroCuenta, _actual.text);
        if (!mounted) return;
        setState(() {
          _desafio = d;
          _codigo.clear();
          _paso = _PasoPin.codigo;
        });
      });

  Future<void> _verificarCodigo() => _ejecutar(() async {
        final id = await widget.session.api
            .verificarCambioPin(widget.cuenta.numeroCuenta, _desafio!.desafioId, _codigo.text.trim());
        if (!mounted) return;
        setState(() {
          _autorizacionId = id;
          _paso = _PasoPin.nuevo;
        });
      });

  Future<void> _guardar() => _ejecutar(() async {
        await widget.session.api.establecerPin(widget.cuenta.numeroCuenta, _nuevo.text,
            pinActual: _conPin ? _actual.text : null, autorizacionId: _autorizacionId);
        if (mounted) Navigator.pop(context, ResultadoPin.guardado);
      });

  Future<void> _olvide() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('¿Olvidaste tu PIN?'),
        content: const Text('Te enviaremos un PIN temporal al correo de tu cuenta. '
            'Tu PIN actual dejará de funcionar y podrás cambiarlo cuando quieras.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Enviar PIN temporal')),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    setState(() {
      _trabajando = true;
      _error = null;
    });
    try {
      await widget.session.api.olvidePin(widget.cuenta.numeroCuenta);
      if (mounted) Navigator.pop(context, ResultadoPin.temporalEnviado);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.mensaje);
    } finally {
      if (mounted) setState(() => _trabajando = false);
    }
  }

  InputDecoration _deco(String label, {IconData icono = Iconos.pin_outlined}) =>
      InputDecoration(labelText: label, counterText: '', prefixIcon: Icon(icono));

  String? _validarPin(String? v) => RegExp(r'^\d{4}$').hasMatch(v ?? '') ? null : 'Deben ser 4 dígitos';

  Widget _campoPin(TextEditingController c, String etiqueta, {String? Function(String?)? validator, bool autofocus = true}) {
    return TextFormField(
      controller: c,
      autofocus: autofocus,
      obscureText: true,
      maxLength: 4,
      keyboardType: TextInputType.number,
      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
      decoration: _deco(etiqueta),
      validator: validator ?? _validarPin,
    );
  }

  List<Widget> _contenido() {
    final t = Theme.of(context);
    switch (_paso) {
      case _PasoPin.actual:
        return [
          const Text('Para cambiar tu PIN primero escribe el actual. Después te mandaremos un código a tu correo.'),
          const SizedBox(height: 16),
          _campoPin(_actual, 'PIN actual'),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton(onPressed: _trabajando ? null : _olvide, child: const Text('Olvidé mi PIN')),
          ),
        ];
      case _PasoPin.codigo:
        return [
          Text('Enviamos un código de 6 dígitos a ${_desafio?.correoEnmascarado ?? 'tu correo'}. '
              'Vence en ${((_desafio?.expiraEnSegundos ?? 300) / 60).round()} minutos.'),
          const SizedBox(height: 16),
          TextFormField(
            controller: _codigo,
            autofocus: true,
            maxLength: 6,
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            decoration: _deco('Código del correo', icono: Iconos.mark_email_read_outlined),
            validator: (v) => RegExp(r'^\d{6}$').hasMatch(v ?? '') ? null : 'Deben ser 6 dígitos',
          ),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton(
              onPressed: _trabajando ? null : () => setState(() => _paso = _PasoPin.actual),
              child: Text('Volver a empezar', style: TextStyle(color: t.colorScheme.onSurfaceVariant)),
            ),
          ),
        ];
      case _PasoPin.nuevo:
        return [
          Text(_conPin
              ? 'Verificación correcta. Elige tu PIN nuevo de 4 dígitos.'
              : 'Elige un PIN de 4 dígitos. Se pide en cada compra.'),
          const SizedBox(height: 16),
          _campoPin(_nuevo, 'PIN nuevo'),
          const SizedBox(height: 12),
          _campoPin(_confirma, 'Confirma el PIN nuevo',
              autofocus: false, validator: (v) => v == _nuevo.text ? null : 'Los PIN no coinciden'),
        ];
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final pasos = _conPin ? 3 : 1;
    final numero = !_conPin ? 1 : _paso.index + 1;
    final accion = switch (_paso) {
      _PasoPin.actual => ('Enviar código', _enviarCodigo),
      _PasoPin.codigo => ('Verificar', _verificarCodigo),
      _PasoPin.nuevo => ('Guardar PIN', _guardar),
    };
    return AlertDialog(
      title: Text(_conPin ? 'Cambiar PIN · ${widget.cuenta.enmascarada}' : 'Crear PIN · ${widget.cuenta.enmascarada}'),
      content: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 400),
        child: Form(
          key: _form,
          // Validación en tiempo real: el error aparece al escribir, no hasta enviar.
          autovalidateMode: AutovalidateMode.onUserInteraction,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (_conPin) ...[
                  LinearProgressIndicator(value: numero / pasos, borderRadius: BorderRadius.circular(4)),
                  const SizedBox(height: 6),
                  Text('Paso $numero de $pasos',
                      style: t.textTheme.labelMedium?.copyWith(color: t.colorScheme.onSurfaceVariant)),
                  const SizedBox(height: 12),
                ],
                ..._contenido(),
                if (_error != null) ...[
                  const SizedBox(height: 12),
                  Text(_error!, style: TextStyle(color: t.colorScheme.error)),
                ],
              ],
            ),
          ),
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancelar')),
        FilledButton(
          onPressed: _trabajando ? null : accion.$2,
          child: _trabajando
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : Text(accion.$1),
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
          icon: Icon(_ocultar ? Iconos.visibility_outlined : Iconos.visibility_off_outlined),
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
                  decoration: _deco('Contraseña actual', Iconos.lock_outline),
                  validator: (v) => (v ?? '').isEmpty ? 'Escribe tu contraseña actual' : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _nueva,
                  obscureText: _ocultar,
                  maxLength: FiltrosEntrada.maxPassword,
                  inputFormatters: formato,
                  autofillHints: const [AutofillHints.newPassword],
                  decoration: _deco('Contraseña nueva', Iconos.lock_reset_outlined),
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
                  decoration: _deco('Confirma la contraseña nueva', Iconos.lock_reset_outlined),
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
