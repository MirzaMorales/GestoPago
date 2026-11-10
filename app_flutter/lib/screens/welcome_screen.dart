import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../legal/documentos_legales.dart';
import '../legal/legal_screen.dart';
import '../state/session.dart';
import '../ui/widgets.dart';
import 'auth/auth_common.dart';

/// Primera pantalla sin sesión.
/// Web: página de presentación; iniciar sesión y registrarse se abren como modal.
/// Móvil: bienvenida a pantalla completa con solo dos opciones.
class WelcomeScreen extends StatefulWidget {
  const WelcomeScreen({super.key, required this.session});

  final Session session;

  @override
  State<WelcomeScreen> createState() => _WelcomeScreenState();
}

class _WelcomeScreenState extends State<WelcomeScreen> {
  @override
  void initState() {
    super.initState();
    final aviso = widget.session.aviso;
    if (aviso != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) mostrarSnack(context, aviso);
        widget.session.aviso = null;
      });
    }
  }

  /// Solo para vista previa: --dart-define=VISTA_MOVIL=true muestra en el navegador la bienvenida móvil.
  static const _forzarMovil = bool.fromEnvironment('VISTA_MOVIL');

  bool get _esWeb => kIsWeb && !_forzarMovil;

  void _abrir(ModoAuth modo) => _esWeb
      ? abrirModalAuth(context, widget.session, modo)
      : abrirPantallaAuth(context, widget.session, modo);

  @override
  Widget build(BuildContext context) =>
      _esWeb ? _LandingWeb(onAbrir: _abrir) : _BienvenidaMovil(onAbrir: _abrir);
}

// ===================================================================== Móvil

class _BienvenidaMovil extends StatelessWidget {
  const _BienvenidaMovil({required this.onAbrir});

  final void Function(ModoAuth) onAbrir;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final cs = t.colorScheme;
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF0B3B37), Color(0xFF0F766E), Color(0xFF14B8A6)],
          ),
        ),
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(28, 24, 28, 28),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Spacer(flex: 3),
                const _Logo(tamano: 88, claro: true),
                const SizedBox(height: 28),
                Text('GestoPago',
                    textAlign: TextAlign.center,
                    style: t.textTheme.displaySmall
                        ?.copyWith(color: Colors.white, fontWeight: FontWeight.w800, letterSpacing: -0.5)),
                const SizedBox(height: 12),
                Text('Recargas, tarjetas de regalo y pago de servicios\nen un solo lugar.',
                    textAlign: TextAlign.center,
                    style: t.textTheme.titleMedium?.copyWith(color: Colors.white.withValues(alpha: 0.85))),
                const Spacer(flex: 4),
                FilledButton(
                  style: FilledButton.styleFrom(
                    backgroundColor: Colors.white,
                    foregroundColor: cs.primary,
                    minimumSize: const Size.fromHeight(56),
                  ),
                  onPressed: () => onAbrir(ModoAuth.login),
                  child: const Text('Iniciar sesión'),
                ),
                const SizedBox(height: 14),
                OutlinedButton(
                  style: OutlinedButton.styleFrom(
                    foregroundColor: Colors.white,
                    side: const BorderSide(color: Colors.white70, width: 1.5),
                    minimumSize: const Size.fromHeight(56),
                    textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
                  ),
                  onPressed: () => onAbrir(ModoAuth.registro),
                  child: const Text('Soy nuevo, crear cuenta'),
                ),
                const SizedBox(height: 20),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.verified_user_outlined, size: 16, color: Colors.white.withValues(alpha: 0.75)),
                    const SizedBox(width: 6),
                    Flexible(
                      child: Text('Protegido con verificación en dos pasos',
                          textAlign: TextAlign.center,
                          style: t.textTheme.bodySmall?.copyWith(color: Colors.white.withValues(alpha: 0.75))),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Wrap(
                  alignment: WrapAlignment.center,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    for (final (texto, doc) in const [
                      ('Privacidad', avisoPrivacidad),
                      ('Términos', terminosCondiciones),
                    ])
                      TextButton(
                        style: TextButton.styleFrom(
                          foregroundColor: Colors.white.withValues(alpha: 0.85),
                          textStyle: t.textTheme.bodySmall?.copyWith(decoration: TextDecoration.underline),
                          visualDensity: VisualDensity.compact,
                        ),
                        onPressed: () => LegalScreen.abrir(context, doc),
                        child: Text(texto),
                      ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

// ===================================================================== Web

class _LandingWeb extends StatelessWidget {
  const _LandingWeb({required this.onAbrir});

  final void Function(ModoAuth) onAbrir;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SingleChildScrollView(
        child: Column(
          children: [
            _Hero(onAbrir: onAbrir),
            const _Caracteristicas(),
            const _ComoFunciona(),
            _Cierre(onAbrir: onAbrir),
            const _Pie(),
          ],
        ),
      ),
    );
  }
}

class _Hero extends StatelessWidget {
  const _Hero({required this.onAbrir});

  final void Function(ModoAuth) onAbrir;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final ancho = MediaQuery.sizeOf(context).width;
    final compacto = ancho < 860;

    final texto = Column(
      crossAxisAlignment: compacto ? CrossAxisAlignment.center : CrossAxisAlignment.start,
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: 0.15),
            borderRadius: BorderRadius.circular(20),
          ),
          child: Text('Conectado a la red de pagos GestoPago',
              style: t.textTheme.labelMedium?.copyWith(color: Colors.white)),
        ),
        const SizedBox(height: 20),
        Text(
          'Paga tus servicios y recargas en segundos',
          textAlign: compacto ? TextAlign.center : TextAlign.start,
          style: (compacto ? t.textTheme.headlineLarge : t.textTheme.displayMedium)?.copyWith(
              color: Colors.white, fontWeight: FontWeight.w800, height: 1.1, letterSpacing: -1),
        ),
        const SizedBox(height: 18),
        Text(
          'Recargas de tiempo aire, tarjetas de regalo y pago de luz, agua, internet y más. '
          'Desde la web o desde tu celular, con la misma cuenta.',
          textAlign: compacto ? TextAlign.center : TextAlign.start,
          style: t.textTheme.titleMedium?.copyWith(color: Colors.white.withValues(alpha: 0.88), height: 1.5),
        ),
        const SizedBox(height: 32),
        Wrap(
          spacing: 14,
          runSpacing: 12,
          alignment: compacto ? WrapAlignment.center : WrapAlignment.start,
          children: [
            FilledButton.icon(
              style: FilledButton.styleFrom(
                backgroundColor: Colors.white,
                foregroundColor: t.colorScheme.primary,
                padding: const EdgeInsets.symmetric(horizontal: 28),
              ),
              onPressed: () => onAbrir(ModoAuth.registro),
              icon: const Icon(Icons.person_add_alt_1),
              label: const Text('Crear cuenta gratis'),
            ),
            OutlinedButton.icon(
              style: OutlinedButton.styleFrom(
                foregroundColor: Colors.white,
                side: const BorderSide(color: Colors.white70, width: 1.5),
                padding: const EdgeInsets.symmetric(horizontal: 28),
                textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
              ),
              onPressed: () => onAbrir(ModoAuth.login),
              icon: const Icon(Icons.login),
              label: const Text('Iniciar sesión'),
            ),
          ],
        ),
      ],
    );

    return Container(
      width: double.infinity,
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF0B3B37), Color(0xFF0F766E), Color(0xFF14B8A6)],
        ),
      ),
      child: SafeArea(
        bottom: false,
        child: Centrado(
          maxWidth: 1180,
          child: Padding(
            padding: EdgeInsets.fromLTRB(24, 20, 24, compacto ? 56 : 96),
            child: Column(
              children: [
                // Barra superior
                Row(
                  children: [
                    const _Logo(tamano: 40, claro: true),
                    const SizedBox(width: 10),
                    Text('GestoPago',
                        style: t.textTheme.titleLarge
                            ?.copyWith(color: Colors.white, fontWeight: FontWeight.w800)),
                    const Spacer(),
                    TextButton(
                      style: TextButton.styleFrom(foregroundColor: Colors.white),
                      onPressed: () => onAbrir(ModoAuth.login),
                      child: const Text('Iniciar sesión'),
                    ),
                  ],
                ),
                SizedBox(height: compacto ? 40 : 80),
                compacto
                    ? texto
                    : Row(
                        children: [
                          Expanded(flex: 6, child: texto),
                          const SizedBox(width: 48),
                          const Expanded(flex: 5, child: _Maqueta()),
                        ],
                      ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Ilustración del producto: una tarjeta de compra exitosa flotando sobre el hero.
class _Maqueta extends StatelessWidget {
  const _Maqueta();

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    Widget fila(IconData icono, String titulo, String sub, String monto) => Padding(
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: Row(
            children: [
              CircleAvatar(
                radius: 20,
                backgroundColor: t.colorScheme.primaryContainer,
                child: Icon(icono, color: t.colorScheme.primary, size: 20),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(titulo, style: t.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
                    Text(sub, style: t.textTheme.bodySmall),
                  ],
                ),
              ),
              Text(monto, style: t.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w800)),
            ],
          ),
        );

    return ExcludeSemantics(
      child: Transform.rotate(
        angle: -0.03,
        child: Container(
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(24),
            boxShadow: [
              BoxShadow(color: Colors.black.withValues(alpha: 0.25), blurRadius: 40, offset: const Offset(0, 20)),
            ],
          ),
          child: Theme(
            data: t.copyWith(brightness: Brightness.light),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(Icons.check_circle_rounded, color: Color(0xFF15803D), size: 28),
                    const SizedBox(width: 10),
                    Text('Compra exitosa',
                        style: t.textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.w800, color: const Color(0xFF15803D))),
                  ],
                ),
                const Divider(height: 28),
                fila(Icons.phone_android, 'Recarga celular', 'Tiempo aire', r'$100.00'),
                fila(Icons.bolt, 'Luz', 'Pago de servicio', r'$356.40'),
                fila(Icons.card_giftcard, 'Tarjeta de regalo', 'Código digital', r'$200.00'),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Caracteristicas extends StatelessWidget {
  const _Caracteristicas();

  static const _items = [
    (Icons.storefront_outlined, 'Catálogo completo',
        'Cientos de recargas, tarjetas y servicios organizados por categoría, con su precio.'),
    (Icons.flash_on_outlined, 'Compra en pocos pasos',
        'Elige, confirma con tu PIN y recibe el resultado al instante con tu comprobante.'),
    (Icons.receipt_long_outlined, 'Historial de compras',
        'Consulta cada compra exitosa con su fecha, importe y número de autorización.'),
    (Icons.verified_user_outlined, 'Seguridad de verdad',
        'Verificación en dos pasos, una sola sesión activa y comunicación cifrada.'),
  ];

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return _Seccion(
      titulo: 'Todo lo que necesitas para pagar',
      subtitulo: 'Una sola cuenta para la web y para la app móvil.',
      child: LayoutBuilder(
        builder: (context, c) {
          final columnas = c.maxWidth > 900 ? 4 : (c.maxWidth > 560 ? 2 : 1);
          final ancho = (c.maxWidth - (columnas - 1) * 16) / columnas;
          return Wrap(
            spacing: 16,
            runSpacing: 16,
            children: [
              for (final (icono, titulo, texto) in _items)
                SizedBox(
                  width: ancho,
                  child: Card(
                    child: Padding(
                      padding: const EdgeInsets.all(22),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Container(
                            padding: const EdgeInsets.all(10),
                            decoration: BoxDecoration(
                              color: t.colorScheme.primaryContainer,
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: Icon(icono, color: t.colorScheme.onPrimaryContainer),
                          ),
                          const SizedBox(height: 16),
                          Text(titulo, style: t.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
                          const SizedBox(height: 6),
                          Text(texto,
                              style: t.textTheme.bodyMedium
                                  ?.copyWith(color: t.colorScheme.onSurfaceVariant, height: 1.45)),
                        ],
                      ),
                    ),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}

class _ComoFunciona extends StatelessWidget {
  const _ComoFunciona();

  static const _pasos = [
    ('Crea tu cuenta', 'Regístrate con tus datos y obtén tu cuenta digital.'),
    ('Verifica tu acceso', 'Entra con tu correo, contraseña y el código que te enviamos.'),
    ('Elige y paga', 'Selecciona el producto, confirma con tu PIN y listo.'),
  ];

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Container(
      color: t.colorScheme.surfaceContainerLow,
      child: _Seccion(
        titulo: 'Así de fácil',
        child: LayoutBuilder(
          builder: (context, c) {
            final fila = c.maxWidth > 760;
            final hijos = [
              for (var i = 0; i < _pasos.length; i++)
                _PasoNumerado(numero: i + 1, titulo: _pasos[i].$1, texto: _pasos[i].$2),
            ];
            return fila
                ? Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [for (final h in hijos) Expanded(child: h)],
                  )
                : Column(children: [for (final h in hijos) Padding(padding: const EdgeInsets.only(bottom: 20), child: h)]);
          },
        ),
      ),
    );
  }
}

class _PasoNumerado extends StatelessWidget {
  const _PasoNumerado({required this.numero, required this.titulo, required this.texto});

  final int numero;
  final String titulo;
  final String texto;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12),
      child: Column(
        children: [
          CircleAvatar(
            radius: 26,
            backgroundColor: t.colorScheme.primary,
            child: Text('$numero',
                style: TextStyle(
                    color: t.colorScheme.onPrimary, fontSize: 20, fontWeight: FontWeight.w800)),
          ),
          const SizedBox(height: 14),
          Text(titulo, style: t.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
          const SizedBox(height: 6),
          Text(texto,
              textAlign: TextAlign.center,
              style: t.textTheme.bodyMedium?.copyWith(color: t.colorScheme.onSurfaceVariant)),
        ],
      ),
    );
  }
}

class _Cierre extends StatelessWidget {
  const _Cierre({required this.onAbrir});

  final void Function(ModoAuth) onAbrir;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return _Seccion(
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 40),
        decoration: BoxDecoration(
          color: t.colorScheme.primary,
          borderRadius: BorderRadius.circular(24),
        ),
        child: Column(
          children: [
            Text('¿Listo para empezar?',
                textAlign: TextAlign.center,
                style: t.textTheme.headlineSmall
                    ?.copyWith(color: t.colorScheme.onPrimary, fontWeight: FontWeight.w800)),
            const SizedBox(height: 8),
            Text('Crea tu cuenta en un par de minutos.',
                textAlign: TextAlign.center,
                style: t.textTheme.bodyLarge?.copyWith(color: t.colorScheme.onPrimary.withValues(alpha: 0.85))),
            const SizedBox(height: 24),
            Wrap(
              spacing: 12,
              runSpacing: 12,
              alignment: WrapAlignment.center,
              children: [
                FilledButton(
                  style: FilledButton.styleFrom(
                    backgroundColor: Colors.white,
                    foregroundColor: t.colorScheme.primary,
                    padding: const EdgeInsets.symmetric(horizontal: 28),
                  ),
                  onPressed: () => onAbrir(ModoAuth.registro),
                  child: const Text('Crear cuenta'),
                ),
                TextButton(
                  style: TextButton.styleFrom(foregroundColor: t.colorScheme.onPrimary),
                  onPressed: () => onAbrir(ModoAuth.login),
                  child: const Text('Ya tengo cuenta'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _Pie extends StatelessWidget {
  const _Pie();

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 8, 24, 32),
      child: Column(
        children: [
          Wrap(
            alignment: WrapAlignment.center,
            spacing: 8,
            children: [
              TextButton.icon(
                onPressed: () => LegalScreen.abrir(context, avisoPrivacidad),
                icon: const Icon(Icons.privacy_tip_outlined, size: 18),
                label: const Text('Aviso de Privacidad'),
              ),
              TextButton.icon(
                onPressed: () => LegalScreen.abrir(context, terminosCondiciones),
                icon: const Icon(Icons.gavel_outlined, size: 18),
                label: const Text('Términos y Condiciones'),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            '© ${DateTime.now().year} GestoPago App · Proyecto académico de comercio digital. '
            'Pagos procesados por GestoPago.',
            textAlign: TextAlign.center,
            style: t.textTheme.bodySmall?.copyWith(color: t.colorScheme.onSurfaceVariant),
          ),
        ],
      ),
    );
  }
}

class _Seccion extends StatelessWidget {
  const _Seccion({this.titulo, this.subtitulo, required this.child});

  final String? titulo;
  final String? subtitulo;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Centrado(
      maxWidth: 1180,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 56),
        child: Column(
          children: [
            if (titulo != null)
              Text(titulo!,
                  textAlign: TextAlign.center,
                  style: t.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w800)),
            if (subtitulo != null) ...[
              const SizedBox(height: 8),
              Text(subtitulo!,
                  textAlign: TextAlign.center,
                  style: t.textTheme.titleMedium?.copyWith(color: t.colorScheme.onSurfaceVariant)),
            ],
            if (titulo != null) const SizedBox(height: 36),
            child,
          ],
        ),
      ),
    );
  }
}

class _Logo extends StatelessWidget {
  const _Logo({required this.tamano, this.claro = false});

  final double tamano;
  final bool claro;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Center(
      child: Container(
        width: tamano,
        height: tamano,
        decoration: BoxDecoration(
          color: claro ? Colors.white : cs.primary,
          borderRadius: BorderRadius.circular(tamano * 0.28),
          boxShadow: claro
              ? [BoxShadow(color: Colors.black.withValues(alpha: 0.2), blurRadius: 24, offset: const Offset(0, 10))]
              : null,
        ),
        child: Icon(Icons.payments_rounded, size: tamano * 0.55, color: claro ? cs.primary : cs.onPrimary),
      ),
    );
  }
}
