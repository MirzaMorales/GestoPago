import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/api_client.dart';
import '../../api/models.dart';
import '../../state/session.dart';
import '../../ui/filtros_entrada.dart';
import 'auth_common.dart';

/// "Olvidé mi contraseña": 1) correo → se envía un código; 2) código + contraseña nueva.
/// El servidor responde igual exista o no la cuenta, así que el mensaje tampoco lo revela.
class RecuperarPanel extends StatefulWidget {
  const RecuperarPanel({
    super.key,
    required this.session,
    required this.onVolver,
    required this.onRestablecida,
    this.correoInicial,
  });

  final Session session;
  final VoidCallback onVolver;
  final ValueChanged<String> onRestablecida;
  final String? correoInicial;

  @override
  State<RecuperarPanel> createState() => _RecuperarPanelState();
}

class _RecuperarPanelState extends State<RecuperarPanel> {
  final _formCorreo = GlobalKey<FormState>();
  final _formNueva = GlobalKey<FormState>();
  late final _correo = TextEditingController(text: widget.correoInicial);
  final _codigo = TextEditingController();
  final _nueva = TextEditingController();
  final _confirma = TextEditingController();

  Desafio2fa? _desafio;
  bool _enviando = false;
  bool _ocultar = true;
  String? _error;
  int _restante = 0;
  Timer? _timer;

  @override
  void dispose() {
    _timer?.cancel();
    for (final c in [_correo, _codigo, _nueva, _confirma]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _ejecutar(Future<void> Function() accion) async {
    setState(() {
      _enviando = true;
      _error = null;
    });
    try {
      await accion();
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.mensaje);
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  Future<void> _solicitar() async {
    if (!(_formCorreo.currentState?.validate() ?? true)) return;
    await _ejecutar(() async {
      final d = await widget.session.api.solicitarRecuperacion(_correo.text.trim());
      if (!mounted) return;
      _codigo.clear();
      _timer?.cancel();
      setState(() {
        _desafio = d;
        _restante = d.expiraEnSegundos;
      });
      _timer = Timer.periodic(const Duration(seconds: 1), (t) {
        if (!mounted || _restante <= 0) {
          t.cancel();
        } else {
          setState(() => _restante--);
        }
      });
    });
  }

  Future<void> _restablecer() async {
    if (!(_formNueva.currentState?.validate() ?? false)) return;
    await _ejecutar(() async {
      await widget.session.api.restablecerPassword(_desafio!.desafioId, _codigo.text.trim(), _nueva.text);
      if (mounted) widget.onRestablecida(_correo.text.trim().toLowerCase());
    });
  }

  String get _tiempo => '${_restante ~/ 60}:${(_restante % 60).toString().padLeft(2, '0')}';

  @override
  Widget build(BuildContext context) {
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 250),
      child: _desafio == null ? _pasoCorreo() : _pasoNueva(Theme.of(context)),
    );
  }

  Widget _pasoCorreo() {
    return Form(
      key: _formCorreo,
      autovalidateMode: AutovalidateMode.onUserInteraction,
      child: Column(
        key: const ValueKey('correo'),
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const TituloAuth(
            titulo: 'Recupera tu contraseña',
            subtitulo: 'Te enviaremos un código a tu correo para crear una contraseña nueva.',
          ),
          const SizedBox(height: 24),
          TextFormField(
            controller: _correo,
            autofocus: true,
            keyboardType: TextInputType.emailAddress,
            maxLength: 100,
            inputFormatters: [FiltrosEntrada.correo],
            autofillHints: const [AutofillHints.email],
            decoration: const InputDecoration(
                labelText: 'Correo electrónico', prefixIcon: Icon(Icons.mail_outline), counterText: ''),
            validator: validarCorreo,
            onFieldSubmitted: (_) => _solicitar(),
          ),
          if (_error != null) ...[const SizedBox(height: 16), AlertaAuth(_error!)],
          const SizedBox(height: 24),
          BotonPrincipal(texto: 'Enviar código', cargando: _enviando, onPressed: _solicitar),
          const SizedBox(height: 8),
          TextButton(onPressed: _enviando ? null : widget.onVolver, child: const Text('Volver a iniciar sesión')),
        ],
      ),
    );
  }

  Widget _pasoNueva(ThemeData t) {
    final expirado = _restante <= 0;
    return Form(
      key: _formNueva,
      autovalidateMode: AutovalidateMode.onUserInteraction,
      child: Column(
        key: const ValueKey('nueva'),
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              IconButton(
                tooltip: 'Volver',
                onPressed: _enviando ? null : () => setState(() => _desafio = null),
                icon: const Icon(Icons.arrow_back),
              ),
              Expanded(
                child: Text('Crea tu nueva contraseña',
                    style: t.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            'Si ${_desafio!.correoEnmascarado} tiene una cuenta, le enviamos un código de 6 dígitos.',
            style: t.textTheme.bodyMedium,
          ),
          const SizedBox(height: 20),
          TextFormField(
            controller: _codigo,
            keyboardType: TextInputType.number,
            maxLength: 6,
            textAlign: TextAlign.center,
            autofillHints: const [AutofillHints.oneTimeCode],
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            style: t.textTheme.titleLarge?.copyWith(letterSpacing: 10, fontWeight: FontWeight.w700),
            decoration: InputDecoration(
              labelText: 'Código',
              counterText: '',
              helperText: expirado ? null : 'Vence en $_tiempo',
              errorText: expirado ? 'El código expiró. Solicita uno nuevo.' : null,
            ),
            validator: (v) => RegExp(r'^\d{6}$').hasMatch(v ?? '') ? null : 'El código tiene 6 dígitos',
          ),
          const SizedBox(height: 14),
          TextFormField(
            controller: _nueva,
            obscureText: _ocultar,
            maxLength: FiltrosEntrada.maxPassword,
            inputFormatters: [FiltrosEntrada.sinControl],
            autofillHints: const [AutofillHints.newPassword],
            decoration: InputDecoration(
              labelText: 'Contraseña nueva',
              counterText: '',
              prefixIcon: const Icon(Icons.lock_outline),
              suffixIcon: IconButton(
                tooltip: _ocultar ? 'Mostrar contraseña' : 'Ocultar contraseña',
                icon: Icon(_ocultar ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                onPressed: () => setState(() => _ocultar = !_ocultar),
              ),
            ),
            onChanged: (_) => setState(() {}),
            validator: validarContrasenaSegura,
          ),
          const SizedBox(height: 8),
          ReglasContrasena(valor: _nueva.text),
          const SizedBox(height: 14),
          TextFormField(
            controller: _confirma,
            obscureText: _ocultar,
            maxLength: FiltrosEntrada.maxPassword,
            inputFormatters: [FiltrosEntrada.sinControl],
            decoration: const InputDecoration(
                labelText: 'Confirma la contraseña nueva',
                counterText: '',
                prefixIcon: Icon(Icons.lock_reset_outlined)),
            validator: (v) => v == _nueva.text ? null : 'Las contraseñas no coinciden',
          ),
          if (_error != null) ...[const SizedBox(height: 16), AlertaAuth(_error!)],
          const SizedBox(height: 24),
          BotonPrincipal(
            texto: 'Guardar contraseña',
            cargando: _enviando,
            onPressed: expirado ? null : _restablecer,
          ),
          const SizedBox(height: 8),
          TextButton(
            onPressed: _enviando ? null : _solicitar,
            child: const Text('Enviar un código nuevo'),
          ),
        ],
      ),
    );
  }
}
