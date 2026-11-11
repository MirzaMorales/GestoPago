import 'package:flutter/material.dart';

import '../../state/session.dart';
import 'login_panel.dart';
import 'recuperar_panel.dart';
import 'register_panel.dart';

enum ModoAuth { login, registro, recuperar }

String? validarCorreo(String? v) {
  final s = v?.trim() ?? '';
  if (s.isEmpty) return 'Escribe tu correo';
  if (!RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(s)) return 'Correo no válido';
  return null;
}

class TituloAuth extends StatelessWidget {
  const TituloAuth({super.key, required this.titulo, required this.subtitulo});

  final String titulo;
  final String subtitulo;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(titulo, style: t.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
        const SizedBox(height: 4),
        Text(subtitulo,
            style: t.textTheme.bodyMedium?.copyWith(color: t.colorScheme.onSurfaceVariant)),
      ],
    );
  }
}

class AlertaAuth extends StatelessWidget {
  const AlertaAuth(this.texto, {super.key, this.exito = false});

  final String texto;
  final bool exito;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final fondo = exito ? cs.primaryContainer : cs.errorContainer;
    final frente = exito ? cs.onPrimaryContainer : cs.onErrorContainer;
    return Semantics(
      liveRegion: true,
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(color: fondo, borderRadius: BorderRadius.circular(12)),
        child: Row(
          children: [
            Icon(exito ? Icons.check_circle_outline : Icons.error_outline, color: frente),
            const SizedBox(width: 12),
            Expanded(child: Text(texto, style: TextStyle(color: frente))),
          ],
        ),
      ),
    );
  }
}

class BotonPrincipal extends StatelessWidget {
  const BotonPrincipal({super.key, required this.texto, required this.onPressed, this.cargando = false});

  final String texto;
  final VoidCallback? onPressed;
  final bool cargando;

  @override
  Widget build(BuildContext context) => FilledButton(
        onPressed: cargando ? null : onPressed,
        child: cargando
            ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2.5))
            : Text(texto),
      );
}

/// Alterna entre iniciar sesión y crear cuenta sin salir del contenedor (modal o pantalla).
class FlujoAuth extends StatefulWidget {
  const FlujoAuth({super.key, required this.session, required this.inicial});

  final Session session;
  final ModoAuth inicial;

  @override
  State<FlujoAuth> createState() => _FlujoAuthState();
}

class _FlujoAuthState extends State<FlujoAuth> {
  late ModoAuth _modo = widget.inicial;
  String? _correo;
  String? _mensaje;

  @override
  Widget build(BuildContext context) {
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 250),
      child: _modo == ModoAuth.recuperar
          ? RecuperarPanel(
              key: const ValueKey('recuperar'),
              session: widget.session,
              correoInicial: _correo,
              onVolver: () => setState(() => _modo = ModoAuth.login),
              onRestablecida: (correo) => setState(() {
                _correo = correo;
                _mensaje = 'Tu contraseña se actualizó. Inicia sesión con la nueva.';
                _modo = ModoAuth.login;
              }),
            )
          : _modo == ModoAuth.login
          ? LoginPanel(
              key: ValueKey('login-$_correo'),
              session: widget.session,
              correoInicial: _correo,
              mensajeInicial: _mensaje,
              onCrearCuenta: () => setState(() {
                _modo = ModoAuth.registro;
                _mensaje = null;
              }),
              onOlvidePassword: () => setState(() {
                _modo = ModoAuth.recuperar;
                _mensaje = null;
              }),
            )
          : RegisterPanel(
              key: const ValueKey('registro'),
              session: widget.session,
              onIniciarSesion: () => setState(() => _modo = ModoAuth.login),
              onRegistrado: (correo, cuenta) => setState(() {
                _correo = correo;
                _mensaje = cuenta == null || cuenta.isEmpty
                    ? 'Tu cuenta fue creada. Inicia sesión para continuar.'
                    : 'Tu cuenta fue creada (cuenta terminación ${cuenta.substring(cuenta.length - 4)}). '
                        'Inicia sesión para continuar.';
                _modo = ModoAuth.login;
              }),
            ),
    );
  }
}

/// Web: modal centrado sobre la página de bienvenida.
Future<void> abrirModalAuth(BuildContext context, Session session, ModoAuth modo) {
  return showDialog<void>(
    context: context,
    barrierColor: Colors.black54,
    builder: (ctx) {
      final ancho = MediaQuery.sizeOf(ctx).width;
      return Dialog(
        insetPadding: EdgeInsets.symmetric(horizontal: ancho < 600 ? 12 : 40, vertical: 24),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        clipBehavior: Clip.antiAlias,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 560),
          child: Stack(
            children: [
              SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(28, 32, 28, 24),
                child: FlujoAuth(session: session, inicial: modo),
              ),
              Positioned(
                top: 8,
                right: 8,
                child: IconButton(
                  tooltip: 'Cerrar',
                  icon: const Icon(Icons.close),
                  onPressed: () => Navigator.pop(ctx),
                ),
              ),
            ],
          ),
        ),
      );
    },
  );
}

/// Móvil: pantalla completa.
Future<void> abrirPantallaAuth(BuildContext context, Session session, ModoAuth modo) {
  return Navigator.of(context).push(MaterialPageRoute(
    builder: (_) => Scaffold(
      appBar: AppBar(),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
          child: FlujoAuth(session: session, inicial: modo),
        ),
      ),
    ),
  ));
}

/// Reglas de contraseña segura (las mismas que exige el backend).
final reglasContrasena = <(String, RegExp)>[
  ('Mínimo 8 caracteres', RegExp(r'.{8,}')),
  ('Una letra mayúscula', RegExp(r'[A-Z]')),
  ('Una letra minúscula', RegExp(r'[a-z]')),
  ('Un número', RegExp(r'\d')),
  ('Un carácter especial (!@#\$…)', RegExp(r'[^A-Za-z0-9\s]')),
];

String? validarContrasenaSegura(String? v) =>
    reglasContrasena.every((r) => r.$2.hasMatch(v ?? '')) ? null : 'La contraseña no cumple las reglas';

/// Lista de reglas que se va marcando mientras se escribe.
class ReglasContrasena extends StatelessWidget {
  const ReglasContrasena({super.key, required this.valor});

  final String valor;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Wrap(
      spacing: 16,
      runSpacing: 6,
      children: [
        for (final r in reglasContrasena)
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(r.$2.hasMatch(valor) ? Icons.check_circle : Icons.radio_button_unchecked,
                  size: 18, color: r.$2.hasMatch(valor) ? t.colorScheme.primary : t.colorScheme.outline),
              const SizedBox(width: 6),
              Text(r.$1, style: t.textTheme.bodySmall),
            ],
          ),
      ],
    );
  }
}
