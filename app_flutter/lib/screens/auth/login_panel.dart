import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/api_client.dart';
import '../../api/models.dart';
import '../../state/session.dart';
import '../../ui/filtros_entrada.dart';
import '../../ui/widgets.dart';
import 'auth_common.dart';

/// Inicio de sesión en dos pasos: credenciales y código 2FA enviado por correo.
/// Si el usuario ya tiene una sesión abierta en otra plataforma, se le pide confirmar cerrarla.
/// Se usa dentro del modal (web) o de una pantalla completa (móvil).
class LoginPanel extends StatefulWidget {
  const LoginPanel({
    super.key,
    required this.session,
    this.correoInicial,
    this.mensajeInicial,
    required this.onCrearCuenta,
    this.onOlvidePassword,
  });

  final Session session;
  final String? correoInicial;

  /// Aviso positivo al abrir (por ejemplo, "Tu cuenta fue creada").
  final String? mensajeInicial;
  final VoidCallback onCrearCuenta;
  final VoidCallback? onOlvidePassword;

  @override
  State<LoginPanel> createState() => _LoginPanelState();
}

class _LoginPanelState extends State<LoginPanel> {
  final _form = GlobalKey<FormState>();
  late final _correo = TextEditingController(text: widget.correoInicial);
  final _password = TextEditingController();
  bool _ocultar = true;
  bool _enviando = false;
  String? _error;

  Desafio2fa? _desafio;

  /// Si ya se confirmó cerrar la otra sesión, el reenvío del código mantiene esa decisión.
  bool _cerrarAnterior = false;

  @override
  void dispose() {
    _correo.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _ingresar({bool cerrarAnterior = false, bool validar = true}) async {
    if (validar && !(_form.currentState?.validate() ?? false)) return;
    _cerrarAnterior = cerrarAnterior;
    setState(() {
      _enviando = true;
      _error = null;
    });
    try {
      final desafio = await widget.session.api.login(
        _correo.text.trim(),
        _password.text,
        plataforma: widget.session.plataforma,
        cerrarSesionAnterior: cerrarAnterior,
      );
      if (mounted) setState(() => _desafio = desafio);
    } on ApiException catch (e) {
      if (e.status == 409 && e.detalles['error'] == 'SESION_ACTIVA') {
        if (mounted) setState(() => _enviando = false);
        final confirmar = await _confirmarCierre(e.detalles['plataformaActiva']);
        if (confirmar == true) return _ingresar(cerrarAnterior: true, validar: false);
        return;
      }
      if (!mounted) return;
      if (_desafio != null) {
        mostrarSnack(context, e.mensaje, error: true); // reenvío del código desde el paso 2
      } else {
        setState(() => _error = e.mensaje);
      }
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  Future<bool?> _confirmarCierre(String? plataforma) {
    final donde = switch (plataforma) {
      'MOVIL' => 'la aplicación móvil',
      'WEB' => 'la plataforma web',
      _ => 'otro dispositivo',
    };
    return showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        icon: const Icon(Icons.devices_other_rounded, size: 36),
        title: const Text('Ya tienes una sesión activa'),
        content: Text(
          'Tu cuenta está abierta en $donde. Solo se permite una sesión a la vez.\n\n'
          '¿Quieres cerrar esa sesión y continuar aquí?',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancelar')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Cerrar la otra sesión')),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 250),
      child: _desafio == null
          ? _credenciales(Theme.of(context))
          : _Paso2fa(
              key: ValueKey(_desafio!.desafioId),
              session: widget.session,
              desafio: _desafio!,
              onVolver: () => setState(() => _desafio = null),
              onReenviar: () => _ingresar(cerrarAnterior: _cerrarAnterior, validar: false),
            ),
    );
  }

  Widget _credenciales(ThemeData t) {
    return Form(
      key: _form,
      // Validación en tiempo real: el error aparece al escribir, no hasta enviar.
      autovalidateMode: AutovalidateMode.onUserInteraction,
      child: AutofillGroup(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const TituloAuth(
              titulo: 'Bienvenido de nuevo',
              subtitulo: 'Usa las mismas credenciales en la web y en la app.',
            ),
            if (widget.mensajeInicial != null) ...[
              const SizedBox(height: 16),
              AlertaAuth(widget.mensajeInicial!, exito: true),
            ],
            const SizedBox(height: 24),
            TextFormField(
              controller: _correo,
              maxLength: 100,
              inputFormatters: [FiltrosEntrada.correo],
              keyboardType: TextInputType.emailAddress,
              autofillHints: const [AutofillHints.email],
              textInputAction: TextInputAction.next,
              decoration: const InputDecoration(
                  labelText: 'Correo electrónico', prefixIcon: Icon(Icons.mail_outline), counterText: ''),
              validator: validarCorreo,
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _password,
              maxLength: FiltrosEntrada.maxPassword,
              inputFormatters: [FiltrosEntrada.sinControl],
              obscureText: _ocultar,
              autofocus: widget.correoInicial != null,
              autofillHints: const [AutofillHints.password],
              textInputAction: TextInputAction.done,
              onFieldSubmitted: (_) => _ingresar(),
              decoration: InputDecoration(
                labelText: 'Contraseña',
                counterText: '',
                prefixIcon: const Icon(Icons.lock_outline),
                suffixIcon: IconButton(
                  tooltip: _ocultar ? 'Mostrar contraseña' : 'Ocultar contraseña',
                  icon: Icon(_ocultar ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                  onPressed: () => setState(() => _ocultar = !_ocultar),
                ),
              ),
              validator: (v) => (v == null || v.isEmpty) ? 'Escribe tu contraseña' : null,
            ),
            if (widget.onOlvidePassword != null)
              Align(
                alignment: Alignment.centerRight,
                child: TextButton(
                  onPressed: _enviando ? null : widget.onOlvidePassword,
                  child: const Text('¿Olvidaste tu contraseña?'),
                ),
              ),
            if (_error != null) ...[
              const SizedBox(height: 16),
              AlertaAuth(_error!),
            ],
            const SizedBox(height: 24),
            BotonPrincipal(
              texto: 'Iniciar sesión',
              cargando: _enviando,
              onPressed: () => _ingresar(),
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text('¿No tienes cuenta?', style: t.textTheme.bodyMedium),
                TextButton(
                  onPressed: _enviando ? null : widget.onCrearCuenta,
                  child: const Text('Crear cuenta'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// Paso 2: código de 6 dígitos. La validación la hace el servidor.
class _Paso2fa extends StatefulWidget {
  const _Paso2fa({
    super.key,
    required this.session,
    required this.desafio,
    required this.onVolver,
    required this.onReenviar,
  });

  final Session session;
  final Desafio2fa desafio;
  final VoidCallback onVolver;
  final VoidCallback onReenviar;

  @override
  State<_Paso2fa> createState() => _Paso2faState();
}

class _Paso2faState extends State<_Paso2fa> {
  final _codigo = TextEditingController();
  bool _enviando = false;
  String? _error;
  late int _restante = widget.desafio.expiraEnSegundos;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(seconds: 1), (t) {
      if (_restante <= 0) {
        t.cancel();
      } else {
        setState(() => _restante--);
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _codigo.dispose();
    super.dispose();
  }

  Future<void> _verificar() async {
    final codigo = _codigo.text.trim();
    if (!RegExp(r'^\d{6}$').hasMatch(codigo)) {
      setState(() => _error = 'El código tiene 6 dígitos');
      return;
    }
    setState(() {
      _enviando = true;
      _error = null;
    });
    try {
      final usuario = await widget.session.api.verificar2fa(widget.desafio.desafioId, codigo);
      await widget.session.iniciar(usuario); // la app cambia sola a la vista principal
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.mensaje);
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  String get _tiempo =>
      '${_restante ~/ 60}:${(_restante % 60).toString().padLeft(2, '0')}';

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final expirado = _restante <= 0;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            IconButton(
              tooltip: 'Volver',
              onPressed: _enviando ? null : widget.onVolver,
              icon: const Icon(Icons.arrow_back),
            ),
            const SizedBox(width: 4),
            Expanded(
              child: Text('Verificación en dos pasos',
                  style: t.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Icon(Icons.mark_email_read_outlined, color: t.colorScheme.primary),
            const SizedBox(width: 12),
            Expanded(
              child: Text.rich(TextSpan(children: [
                const TextSpan(text: 'Enviamos un código de 6 dígitos a '),
                TextSpan(
                    text: widget.desafio.correoEnmascarado,
                    style: const TextStyle(fontWeight: FontWeight.w700)),
              ])),
            ),
          ],
        ),
        const SizedBox(height: 24),
        TextField(
          controller: _codigo,
          autofocus: true,
          keyboardType: TextInputType.number,
          textAlign: TextAlign.center,
          maxLength: 6,
          autofillHints: const [AutofillHints.oneTimeCode],
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          style: t.textTheme.headlineSmall?.copyWith(letterSpacing: 12, fontWeight: FontWeight.w700),
          decoration: const InputDecoration(counterText: '', hintText: '000000'),
          onChanged: (v) {
            if (v.length == 6 && !_enviando) _verificar();
          },
          onSubmitted: (_) => _verificar(),
        ),
        const SizedBox(height: 8),
        Text(
          expirado ? 'El código expiró. Solicita uno nuevo.' : 'El código vence en $_tiempo',
          textAlign: TextAlign.center,
          style: t.textTheme.bodySmall?.copyWith(
              color: expirado ? t.colorScheme.error : t.colorScheme.onSurfaceVariant),
        ),
        if (_error != null) ...[
          const SizedBox(height: 16),
          AlertaAuth(_error!),
        ],
        const SizedBox(height: 24),
        BotonPrincipal(
          texto: 'Verificar e ingresar',
          cargando: _enviando,
          onPressed: expirado ? null : _verificar,
        ),
        const SizedBox(height: 8),
        TextButton(
          onPressed: _enviando ? null : widget.onReenviar,
          child: const Text('Enviar un código nuevo'),
        ),
      ],
    );
  }
}
