import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/api/api_client.dart';
import 'package:gestopago_app/api/gestopago_api.dart';
import 'package:gestopago_app/api/models.dart';
import 'package:gestopago_app/screens/auth/selector_domicilio.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

/// Servidor simulado con dos colonias en 37700 y el catálogo por listas.
GestoPagoApi _api() {
  final cliente = MockClient((req) async {
    Map<String, dynamic> col(String cp, String colonia) => {
          'codigoPostal': cp,
          'colonia': colonia,
          'tipoAsentamiento': 'Colonia',
          'municipio': 'San Miguel de Allende',
          'estado': 'Guanajuato',
        };
    final p = req.url.path;
    dynamic datos;
    if (p == '/catalogos/codigos-postales/37700') {
      datos = [col('37700', 'Centro'), col('37700', 'Guadiana')];
    } else if (p.startsWith('/catalogos/codigos-postales/')) {
      datos = [];
    } else if (p == '/catalogos/estados') {
      datos = ['Guanajuato', 'Jalisco'];
    } else if (p == '/catalogos/municipios') {
      datos = ['San Miguel de Allende'];
    } else if (p == '/catalogos/colonias') {
      datos = [col('37700', 'Centro')];
    } else {
      return http.Response('{}', 404);
    }
    return http.Response(jsonEncode({'status': 200, 'datos': datos}), 200,
        headers: {'content-type': 'application/json; charset=utf-8'});
  });
  return GestoPagoApi(ApiClient(httpClient: cliente));
}

Future<Colonia? Function()> _montar(WidgetTester tester) async {
  Colonia? elegida;
  await tester.pumpWidget(MaterialApp(
    home: Scaffold(
      body: SingleChildScrollView(
        child: SelectorDomicilio(api: _api(), onChanged: (c) => elegida = c),
      ),
    ),
  ));
  return () => elegida;
}

void main() {
  testWidgets('por código postal llena municipio y estado y deja elegir la colonia', (tester) async {
    await tester.binding.setSurfaceSize(const Size(600, 1200));
    final leer = await _montar(tester);

    await tester.enterText(find.widgetWithText(TextField, 'Código postal'), '37700');
    await tester.pumpAndSettle();

    expect(find.text('San Miguel de Allende'), findsOneWidget);
    expect(find.text('Guanajuato'), findsOneWidget);
    expect(leer(), isNull, reason: 'con dos colonias el usuario debe elegir');

    await tester.tap(find.widgetWithText(DropdownMenu<Colonia>, 'Colonia'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Guadiana').last);
    await tester.pumpAndSettle();

    final c = leer();
    expect(c?.colonia, 'Guadiana');
    expect(c?.codigoPostal, '37700');
  });

  testWidgets('código postal inexistente muestra error y no elige nada', (tester) async {
    await tester.binding.setSurfaceSize(const Size(600, 1200));
    final leer = await _montar(tester);

    await tester.enterText(find.widgetWithText(TextField, 'Código postal'), '99999');
    await tester.pumpAndSettle();

    expect(find.text('Este código postal no existe'), findsOneWidget);
    expect(leer(), isNull);
  });

  testWidgets('sin código postal: estado → municipio → colonia completa el CP', (tester) async {
    await tester.binding.setSurfaceSize(const Size(600, 1200));
    final leer = await _montar(tester);

    await tester.tap(find.text('No lo sé'));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(DropdownMenu<String>, 'Estado'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Guanajuato').last);
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(DropdownMenu<String>, 'Municipio o alcaldía'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('San Miguel de Allende').last);
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(DropdownMenu<Colonia>, 'Colonia'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Centro · CP 37700').last);
    await tester.pumpAndSettle();

    expect((leer())?.colonia, 'Centro');
    expect(find.text('37700'), findsOneWidget); // CP de solo lectura ya lleno
  });
}
