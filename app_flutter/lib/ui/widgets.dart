import 'package:flutter/material.dart';

import '../api/api_client.dart';

/// Limita el ancho del contenido en pantallas grandes (web / tablet).
class Centrado extends StatelessWidget {
  const Centrado({super.key, required this.child, this.maxWidth = 1100});

  final Widget child;
  final double maxWidth;

  @override
  Widget build(BuildContext context) => Align(
        alignment: Alignment.topCenter,
        child: ConstrainedBox(constraints: BoxConstraints(maxWidth: maxWidth), child: child),
      );
}

/// Logo del servicio con respaldo si la imagen no carga.
class LogoServicio extends StatelessWidget {
  const LogoServicio({super.key, required this.url, this.size = 48, required this.nombre});

  final String? url;
  final double size;
  final String nombre;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final respaldo = Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
          color: cs.primaryContainer, borderRadius: BorderRadius.circular(size / 4)),
      child: Text(
        nombre.isNotEmpty ? nombre.characters.first.toUpperCase() : '?',
        style: TextStyle(
            color: cs.onPrimaryContainer, fontWeight: FontWeight.w700, fontSize: size * 0.4),
      ),
    );
    if (url == null || url!.isEmpty) return respaldo;
    return Semantics(
      label: 'Logo de $nombre',
      image: true,
      child: Container(
        width: size,
        height: size,
        padding: EdgeInsets.all(size * 0.08),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(size / 4),
          border: Border.all(color: cs.outlineVariant.withValues(alpha: 0.5)),
        ),
        child: Image.network(
          url!,
          fit: BoxFit.contain,
          // En web, el servidor de imágenes de GestoPago no envía CORS: se usa <img> como respaldo.
          webHtmlElementStrategy: WebHtmlElementStrategy.fallback,
          errorBuilder: (_, _, _) => respaldo,
        ),
      ),
    );
  }
}

/// Estado vacío o de error con acción opcional.
class Mensaje extends StatelessWidget {
  const Mensaje({
    super.key,
    required this.icono,
    required this.titulo,
    this.texto,
    this.accion,
    this.textoAccion,
  });

  final IconData icono;
  final String titulo;
  final String? texto;
  final VoidCallback? accion;
  final String? textoAccion;

  factory Mensaje.error(Object e, VoidCallback reintentar) => Mensaje(
        icono: e is ApiException && e.sinConexion ? Icons.wifi_off_rounded : Icons.error_outline,
        titulo: 'No pudimos cargar la información',
        texto: e is ApiException ? e.mensaje : 'Ocurrió un error inesperado.',
        accion: reintentar,
        textoAccion: 'Reintentar',
      );

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icono, size: 56, color: t.colorScheme.outline),
            const SizedBox(height: 16),
            Text(titulo, style: t.textTheme.titleMedium, textAlign: TextAlign.center),
            if (texto != null) ...[
              const SizedBox(height: 8),
              Text(texto!,
                  style: t.textTheme.bodyMedium?.copyWith(color: t.colorScheme.onSurfaceVariant),
                  textAlign: TextAlign.center),
            ],
            if (accion != null) ...[
              const SizedBox(height: 20),
              OutlinedButton.icon(
                  onPressed: accion,
                  icon: const Icon(Icons.refresh),
                  label: Text(textoAccion ?? 'Reintentar')),
            ],
          ],
        ),
      ),
    );
  }
}

/// Fila etiqueta/valor para detalles y comprobantes.
class FilaDato extends StatelessWidget {
  const FilaDato(this.etiqueta, this.valor, {super.key, this.destacado = false});

  final String etiqueta;
  final String valor;
  final bool destacado;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 7),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            flex: 2,
            child: Text(etiqueta,
                style: t.textTheme.bodyMedium?.copyWith(color: t.colorScheme.onSurfaceVariant)),
          ),
          const SizedBox(width: 12),
          Expanded(
            flex: 3,
            child: SelectableText(
              valor.isEmpty ? '—' : valor,
              textAlign: TextAlign.right,
              style: destacado
                  ? t.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)
                  : t.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w500),
            ),
          ),
        ],
      ),
    );
  }
}

void mostrarSnack(BuildContext context, String texto, {bool error = false}) {
  final cs = Theme.of(context).colorScheme;
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(
      content: Text(texto),
      behavior: SnackBarBehavior.floating,
      backgroundColor: error ? cs.error : null,
    ));
}
