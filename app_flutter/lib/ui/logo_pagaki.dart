import 'package:flutter/material.dart';

/// Nombre de la app. Siempre se acompaña de "con GestoPago" donde cabe: es el proveedor de los pagos.
const String nombreApp = 'Pagaki';
const String lemaApp = 'con GestoPago';

/// Logotipo de Pagaki: una "P" trazada de una sola línea (un gesto) y una moneda que cae a su lado.
/// [claro] lo dibuja sobre fondos oscuros: cuadro blanco y marca verde azulada.
class LogoPagaki extends StatelessWidget {
  const LogoPagaki({super.key, this.tamano = 48, this.claro = false, this.sombra = false});

  final double tamano;
  final bool claro;
  final bool sombra;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: nombreApp,
      image: true,
      child: Container(
        width: tamano,
        height: tamano,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(tamano * 0.26),
          gradient: claro
              ? null
              : const LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [Color(0xFF0F766E), Color(0xFF2DD4BF)],
                ),
          color: claro ? Colors.white : null,
          boxShadow: sombra
              ? [BoxShadow(color: Colors.black.withValues(alpha: 0.22), blurRadius: tamano * 0.3, offset: Offset(0, tamano * 0.12))]
              : null,
        ),
        child: CustomPaint(painter: _MarcaPagaki(claro: claro)),
      ),
    );
  }
}

/// Logo + nombre y lema, para barras superiores y pantallas de bienvenida.
class MarcaPagaki extends StatelessWidget {
  const MarcaPagaki({super.key, this.tamano = 40, this.claro = false, this.mostrarLema = true});

  final double tamano;
  final bool claro;
  final bool mostrarLema;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final color = claro ? Colors.white : t.colorScheme.onSurface;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        LogoPagaki(tamano: tamano, claro: claro),
        SizedBox(width: tamano * 0.28),
        Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(nombreApp,
                style: t.textTheme.titleLarge
                    ?.copyWith(color: color, fontWeight: FontWeight.w800, height: 1.05, letterSpacing: -0.3)),
            if (mostrarLema)
              Text(lemaApp,
                  style: t.textTheme.labelSmall?.copyWith(color: color.withValues(alpha: 0.75), height: 1.1)),
          ],
        ),
      ],
    );
  }
}

class _MarcaPagaki extends CustomPainter {
  _MarcaPagaki({required this.claro});

  final bool claro;

  @override
  void paint(Canvas canvas, Size size) {
    final s = size.width;
    Offset p(double x, double y) => Offset(x * s, y * s);

    final trazo = Paint()
      ..color = claro ? const Color(0xFF0F766E) : Colors.white
      ..style = PaintingStyle.stroke
      ..strokeWidth = s * 0.13
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    // La "P": un solo trazo continuo, de abajo hacia arriba y alrededor de la panza.
    final ruta = Path()
      ..moveTo(p(0.36, 0.76).dx, p(0.36, 0.76).dy)
      ..lineTo(p(0.36, 0.25).dx, p(0.36, 0.25).dy)
      ..lineTo(p(0.50, 0.25).dx, p(0.50, 0.25).dy)
      ..arcToPoint(p(0.50, 0.57), radius: Radius.circular(s * 0.16), clockwise: true)
      ..lineTo(p(0.36, 0.57).dx, p(0.36, 0.57).dy);
    canvas.drawPath(ruta, trazo);

    // La moneda: el pago que cae.
    canvas.drawCircle(p(0.72, 0.71), s * 0.085, Paint()..color = const Color(0xFFF59E0B));
  }

  @override
  bool shouldRepaint(_MarcaPagaki old) => old.claro != claro;
}
