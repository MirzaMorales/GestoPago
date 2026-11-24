import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/models.dart';
import 'theme.dart';

/// Tarjeta de la cuenta digital con la proporción de una tarjeta real (85.6 × 54 mm):
/// marca, chip, número (con ojo para verlo completo), titular y saldo disponible.
class TarjetaCuenta extends StatefulWidget {
  const TarjetaCuenta({super.key, required this.cuenta, this.titular});

  final Cuenta cuenta;

  /// Nombre completo del titular; se muestra en mayúsculas como en una tarjeta física.
  final String? titular;

  @override
  State<TarjetaCuenta> createState() => _TarjetaCuentaState();
}

class _TarjetaCuentaState extends State<TarjetaCuenta> {
  bool _verNumero = false;

  String get _numero {
    final n = widget.cuenta.numeroCuenta.replaceAll(RegExp(r'\D'), '');
    if (n.length < 4) return n;
    final grupos = <String>[];
    for (var i = 0; i < n.length; i += 4) {
      grupos.add(n.substring(i, (i + 4).clamp(0, n.length)));
    }
    if (_verNumero) return grupos.join(' ');
    return [for (var i = 0; i < grupos.length - 1; i++) '••••', grupos.last].join(' ');
  }

  Future<void> _copiar() async {
    await Clipboard.setData(ClipboardData(text: widget.cuenta.numeroCuenta));
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(const SnackBar(content: Text('Número de cuenta copiado'), behavior: SnackBarBehavior.floating));
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.cuenta;
    const blanco = Colors.white;
    final suave = blanco.withValues(alpha: 0.72);
    final sombraTexto = [Shadow(color: Colors.black.withValues(alpha: 0.35), blurRadius: 3, offset: const Offset(0, 1))];

    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 440),
        child: Semantics(
          container: true,
          label: 'Cuenta digital terminación ${c.numeroCuenta.length >= 4 ? c.numeroCuenta.substring(c.numeroCuenta.length - 4) : ''}, '
              'saldo disponible ${dinero(c.saldo)}, ${c.activa ? 'activa' : c.estatus}',
          child: AspectRatio(
            aspectRatio: 1.586,
            child: Container(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(22),
                gradient: const LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [Color(0xFF04201E), Color(0xFF0B5550), Color(0xFF0F766E)],
                  stops: [0, 0.55, 1],
                ),
                boxShadow: [
                  BoxShadow(color: Colors.black.withValues(alpha: 0.28), blurRadius: 26, offset: const Offset(0, 14)),
                ],
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(22),
                child: Stack(
                  children: [
                    // Círculos translúcidos y brillo diagonal: dan profundidad como el plástico de una tarjeta.
                    Positioned(right: -70, top: -80, child: _circulo(230, 0.07)),
                    Positioned(right: -20, bottom: -110, child: _circulo(210, 0.06)),
                    Positioned(left: -60, bottom: -90, child: _circulo(160, 0.04)),
                    Positioned.fill(
                      child: DecoratedBox(
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            begin: const Alignment(-1, -1),
                            end: const Alignment(1, 1),
                            colors: [Colors.transparent, blanco.withValues(alpha: 0.10), Colors.transparent],
                            stops: const [0.25, 0.45, 0.65],
                          ),
                        ),
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.fromLTRB(22, 18, 22, 18),
                      child: DefaultTextStyle(
                        style: const TextStyle(color: blanco),
                        child: LayoutBuilder(
                          builder: (context, caja) {
                            final compacta = caja.maxWidth < 300;
                            return Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    const Icon(Icons.payments_rounded, color: blanco, size: 22),
                                    const SizedBox(width: 8),
                                    const Flexible(
                                      child: FittedBox(
                                        fit: BoxFit.scaleDown,
                                        alignment: Alignment.centerLeft,
                                        child: Text('GestoPago',
                                            style: TextStyle(
                                                fontSize: 17, fontWeight: FontWeight.w800, letterSpacing: 0.4)),
                                      ),
                                    ),
                                    const SizedBox(width: 8),
                                    const Spacer(),
                                    _Estado(activa: c.activa, texto: c.activa ? 'ACTIVA' : c.estatus),
                                  ],
                                ),
                                const Spacer(),
                                Row(
                                  crossAxisAlignment: CrossAxisAlignment.end,
                                  children: [
                                    const _Chip(),
                                    const SizedBox(width: 10),
                                    if (!compacta) Icon(Icons.contactless_outlined, color: suave, size: 26),
                                    const SizedBox(width: 10),
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.end,
                                        children: [
                                          FittedBox(
                                            fit: BoxFit.scaleDown,
                                            alignment: Alignment.centerRight,
                                            child: Text('SALDO DISPONIBLE',
                                                style: TextStyle(fontSize: 10, letterSpacing: 1.2, color: suave)),
                                          ),
                                          const SizedBox(height: 2),
                                          FittedBox(
                                            fit: BoxFit.scaleDown,
                                            alignment: Alignment.centerRight,
                                            child: Text(dinero(c.saldo),
                                                style: TextStyle(
                                                    fontSize: 28,
                                                    fontWeight: FontWeight.w800,
                                                    fontFeatures: const [FontFeature.tabularFigures()],
                                                    shadows: sombraTexto)),
                                          ),
                                        ],
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 14),
                                Row(
                                  children: [
                                    Expanded(
                                      child: FittedBox(
                                        fit: BoxFit.scaleDown,
                                        alignment: Alignment.centerLeft,
                                        child: Text(_numero,
                                            style: TextStyle(
                                                fontSize: 21,
                                                letterSpacing: 2.2,
                                                fontWeight: FontWeight.w600,
                                                fontFeatures: const [FontFeature.tabularFigures()],
                                                shadows: sombraTexto)),
                                      ),
                                    ),
                                    _Accion(
                                      icono: _verNumero ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                                      tooltip: _verNumero ? 'Ocultar número' : 'Ver número completo',
                                      onTap: () => setState(() => _verNumero = !_verNumero),
                                    ),
                                    if (_verNumero)
                                      _Accion(icono: Icons.copy_outlined, tooltip: 'Copiar número', onTap: _copiar),
                                  ],
                                ),
                                const SizedBox(height: 12),
                                Row(
                                  crossAxisAlignment: CrossAxisAlignment.end,
                                  children: [
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          Text('TITULAR', style: TextStyle(fontSize: 9, letterSpacing: 1.2, color: suave)),
                                          const SizedBox(height: 2),
                                          Text(
                                            (widget.titular == null || widget.titular!.trim().isEmpty)
                                                ? 'CUENTA DIGITAL'
                                                : widget.titular!.toUpperCase(),
                                            maxLines: 1,
                                            overflow: TextOverflow.ellipsis,
                                            style: TextStyle(
                                                fontSize: 14,
                                                fontWeight: FontWeight.w600,
                                                letterSpacing: 0.8,
                                                shadows: sombraTexto),
                                          ),
                                        ],
                                      ),
                                    ),
                                    const SizedBox(width: 12),
                                    Text('DIGITAL',
                                        style: TextStyle(
                                            fontSize: 13, fontWeight: FontWeight.w800, letterSpacing: 2, color: suave)),
                                  ],
                                ),
                              ],
                            );
                          },
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  static Widget _circulo(double d, double opacidad) => Container(
        width: d,
        height: d,
        decoration: BoxDecoration(shape: BoxShape.circle, color: Colors.white.withValues(alpha: opacidad)),
      );
}

class _Estado extends StatelessWidget {
  const _Estado({required this.activa, required this.texto});

  final bool activa;
  final String texto;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 3),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.16),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: Colors.white.withValues(alpha: 0.25)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 7,
            height: 7,
            decoration: BoxDecoration(
                shape: BoxShape.circle, color: activa ? const Color(0xFF4ADE80) : const Color(0xFFFBBF24)),
          ),
          const SizedBox(width: 6),
          Text(texto, style: const TextStyle(fontSize: 10.5, fontWeight: FontWeight.w700, letterSpacing: 0.8)),
        ],
      ),
    );
  }
}

class _Accion extends StatelessWidget {
  const _Accion({required this.icono, required this.tooltip, required this.onTap});

  final IconData icono;
  final String tooltip;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => IconButton(
        tooltip: tooltip,
        onPressed: onTap,
        visualDensity: VisualDensity.compact,
        constraints: const BoxConstraints.tightFor(width: 34, height: 34),
        padding: EdgeInsets.zero,
        icon: Icon(icono, size: 20, color: Colors.white.withValues(alpha: 0.85)),
      );
}

/// Chip dorado de la tarjeta.
class _Chip extends StatelessWidget {
  const _Chip();

  @override
  Widget build(BuildContext context) => Container(
        width: 46,
        height: 35,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(7),
          gradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFFF1D88B), Color(0xFFC9992F), Color(0xFFE9C766)],
          ),
          border: Border.all(color: Colors.black.withValues(alpha: 0.18), width: 0.8),
        ),
        child: CustomPaint(painter: _ChipPainter()),
      );
}

class _ChipPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final linea = Paint()
      ..color = Colors.black.withValues(alpha: 0.28)
      ..strokeWidth = 0.9
      ..style = PaintingStyle.stroke;
    final w = size.width, h = size.height;
    // Pistas del chip: tres líneas horizontales cortadas por un rectángulo central.
    canvas.drawLine(Offset(0, h * 0.33), Offset(w * 0.30, h * 0.33), linea);
    canvas.drawLine(Offset(0, h * 0.67), Offset(w * 0.30, h * 0.67), linea);
    canvas.drawLine(Offset(w * 0.70, h * 0.33), Offset(w, h * 0.33), linea);
    canvas.drawLine(Offset(w * 0.70, h * 0.67), Offset(w, h * 0.67), linea);
    canvas.drawLine(Offset(w * 0.30, 0), Offset(w * 0.30, h), linea);
    canvas.drawLine(Offset(w * 0.70, 0), Offset(w * 0.70, h), linea);
    canvas.drawRRect(
      RRect.fromRectAndRadius(Rect.fromLTWH(w * 0.30, h * 0.28, w * 0.40, h * 0.44), const Radius.circular(3)),
      linea,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
