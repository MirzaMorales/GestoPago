import 'package:flutter/material.dart';

import '../ui/widgets.dart';
import 'documentos_legales.dart';

/// Muestra un documento legal: primero "En pocas palabras" y después el texto completo.
class LegalScreen extends StatelessWidget {
  const LegalScreen({super.key, required this.documento});

  final DocumentoLegal documento;

  static Future<void> abrir(BuildContext context, DocumentoLegal documento) => Navigator.of(context)
      .push(MaterialPageRoute(builder: (_) => LegalScreen(documento: documento)));

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(documento.titulo)),
      body: SelectionArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 40),
          children: [
            Centrado(
              maxWidth: 760,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Versión $versionLegal · Última actualización: $fechaLegal',
                      style: t.textTheme.bodySmall?.copyWith(color: t.colorScheme.onSurfaceVariant)),
                  const SizedBox(height: 16),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      color: t.colorScheme.primaryContainer.withValues(alpha: 0.55),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Icon(Icons.lightbulb_outline, color: t.colorScheme.primary),
                            const SizedBox(width: 8),
                            Text('En pocas palabras',
                                style: t.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
                          ],
                        ),
                        const SizedBox(height: 12),
                        for (final r in documento.resumen)
                          Padding(
                            padding: const EdgeInsets.only(bottom: 8),
                            child: Row(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Icon(Icons.check_circle, size: 18, color: t.colorScheme.primary),
                                const SizedBox(width: 10),
                                Expanded(child: Text(r, style: t.textTheme.bodyMedium?.copyWith(height: 1.45))),
                              ],
                            ),
                          ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 8),
                  for (final s in documento.secciones) _Seccion(seccion: s),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Seccion extends StatelessWidget {
  const _Seccion({required this.seccion});

  final SeccionLegal seccion;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final cuerpo = t.textTheme.bodyMedium?.copyWith(height: 1.55);
    return Padding(
      padding: EdgeInsets.only(top: seccion.titulo.isEmpty ? 4 : 22),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (seccion.titulo.isNotEmpty) ...[
            Semantics(
              header: true,
              child: Text(seccion.titulo,
                  style: t.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
            ),
            const SizedBox(height: 8),
          ],
          for (final p in seccion.parrafos)
            Padding(padding: const EdgeInsets.only(bottom: 8), child: Text(p, style: cuerpo)),
          for (final p in seccion.puntos)
            Padding(
              padding: const EdgeInsets.only(left: 4, bottom: 6),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Container(
                      width: 6,
                      height: 6,
                      decoration: BoxDecoration(color: t.colorScheme.primary, shape: BoxShape.circle),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(child: Text(p, style: cuerpo)),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
