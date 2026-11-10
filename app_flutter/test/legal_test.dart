import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/legal/documentos_legales.dart';
import 'package:gestopago_app/legal/legal_screen.dart';
import 'package:gestopago_app/screens/welcome_screen.dart';
import 'package:gestopago_app/state/session.dart';

void main() {
  test('la versión legal de la app coincide con app.legal.version del backend', () {
    final props = File('../src/main/resources/application.properties').readAsLinesSync();
    final linea = props.firstWhere((l) => l.startsWith('app.legal.version='));
    expect(linea.split('=').last.trim(), versionLegal,
        reason: 'Si cambias los documentos, actualiza la versión en ambos lados');
  });

  test('el aviso de privacidad cubre los apartados que exige la ley', () {
    final titulos = avisoPrivacidad.secciones.map((s) => s.titulo.toLowerCase()).join(' | ');
    for (final tema in ['responsable', 'datos que recabamos', 'para qué', 'compartimos', 'protegemos',
        'conservamos', 'arco', 'cambios']) {
      expect(titulos, contains(tema));
    }
  });

  testWidgets('pantalla legal muestra el resumen y el texto completo', (tester) async {
    await tester.binding.setSurfaceSize(const Size(800, 3000));
    await tester.pumpWidget(const MaterialApp(home: LegalScreen(documento: avisoPrivacidad)));
    expect(find.text('Aviso de Privacidad'), findsOneWidget);
    expect(find.text('En pocas palabras'), findsOneWidget);
    expect(find.textContaining('derechos ARCO'), findsWidgets);
  });

  testWidgets('desde la bienvenida móvil se abren privacidad y términos', (tester) async {
    await tester.binding.setSurfaceSize(const Size(400, 900));
    await tester.pumpWidget(MaterialApp(home: WelcomeScreen(session: Session())));

    await tester.tap(find.text('Privacidad'));
    await tester.pumpAndSettle();
    expect(find.text('Aviso de Privacidad'), findsOneWidget);

    await tester.pageBack();
    await tester.pumpAndSettle();
    await tester.tap(find.text('Términos'));
    await tester.pumpAndSettle();
    expect(find.text('Términos y Condiciones'), findsOneWidget);
  });
}
