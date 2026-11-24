import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/api/api_client.dart';
import 'package:gestopago_app/api/models.dart';
import 'package:gestopago_app/screens/account_screen.dart';
import 'package:gestopago_app/state/session.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:intl/date_symbol_data_local.dart';

final llamadas = <String, Map<String, dynamic>>{};

Session _sesion({required bool tienePin}) {
  final s = Session(
    client: ApiClient(
      httpClient: MockClient((req) async {
        final cuerpo = jsonDecode(req.body.isEmpty ? '{}' : req.body) as Map<String, dynamic>;
        llamadas[req.url.path] = cuerpo;
        dynamic respuesta = {'datos': {}};
        if (req.url.path == '/clientes/buscar') {
          respuesta = {
            'datos': [
              {
                'nombre': 'Ana',
                'apellidoPaterno': 'López',
                'correo': 'ana@correo.com',
                'cuentas': [
                  {'numeroCuenta': '1234567890129604', 'saldo': 100, 'estatus': 'ACTIVA', 'tienePin': tienePin}
                ],
              }
            ]
          };
        } else if (req.url.path == '/cuentas/pin/cambio/iniciar') {
          respuesta = {'requiere2fa': true, 'desafioId': 'd1', 'correoEnmascarado': 'a***@correo.com', 'expiraEnSegundos': 300};
        } else if (req.url.path == '/cuentas/pin/cambio/verificar') {
          respuesta = {'datos': {'autorizacionId': 'auth-1'}};
        }
        return http.Response(jsonEncode(respuesta), 200,
            headers: {'content-type': 'application/json; charset=utf-8'});
      }),
    ),
  );
  s.usuario = Usuario(token: 't', correo: 'ana@correo.com', usuarioId: 1, clienteId: 1, rol: 'CLIENTE');
  return s;
}

Future<void> _abrir(WidgetTester tester, Session s, String boton) async {
  await tester.binding.setSurfaceSize(const Size(1000, 1400));
  await tester.pumpWidget(MaterialApp(home: AccountScreen(session: s)));
  await tester.pumpAndSettle();
  await tester.tap(find.text(boton));
  await tester.pumpAndSettle();
}

void main() {
  setUpAll(() => initializeDateFormatting('es_MX'));
  setUp(() {
    FlutterSecureStorage.setMockInitialValues({});
    llamadas.clear();
  });

  testWidgets('cambiar el PIN: PIN actual, código del correo y luego el PIN nuevo', (tester) async {
    await _abrir(tester, _sesion(tienePin: true), 'Cambiar PIN de compras');

    expect(find.text('Paso 1 de 3'), findsOneWidget);
    await tester.enterText(find.byType(TextFormField), '1111');
    await tester.tap(find.text('Enviar código'));
    await tester.pumpAndSettle();
    expect(llamadas['/cuentas/pin/cambio/iniciar']!['pinActual'], '1111');

    expect(find.text('Paso 2 de 3'), findsOneWidget);
    expect(find.textContaining('a***@correo.com'), findsOneWidget);
    expect(find.text('PIN nuevo'), findsNothing); // el campo del PIN nuevo aparece hasta verificar
    await tester.enterText(find.byType(TextFormField), '123456');
    await tester.tap(find.text('Verificar'));
    await tester.pumpAndSettle();
    expect(llamadas['/cuentas/pin/cambio/verificar']!['codigo'], '123456');

    expect(find.text('Paso 3 de 3'), findsOneWidget);
    await tester.enterText(find.widgetWithText(TextFormField, 'PIN nuevo'), '2222');
    await tester.enterText(find.widgetWithText(TextFormField, 'Confirma el PIN nuevo'), '2222');
    await tester.tap(find.text('Guardar PIN'));
    await tester.pumpAndSettle();

    final guardado = llamadas['/cuentas/pin']!;
    expect(guardado['pinNuevo'], '2222');
    expect(guardado['pinActual'], '1111');
    expect(guardado['autorizacionId'], 'auth-1');
    expect(find.text('PIN guardado correctamente.'), findsOneWidget);
  });

  testWidgets('el PIN actual con menos de 4 dígitos no envía el código', (tester) async {
    await _abrir(tester, _sesion(tienePin: true), 'Cambiar PIN de compras');

    await tester.enterText(find.byType(TextFormField), '12');
    await tester.tap(find.text('Enviar código'));
    await tester.pumpAndSettle();

    expect(find.text('Deben ser 4 dígitos'), findsOneWidget);
    expect(llamadas.containsKey('/cuentas/pin/cambio/iniciar'), isFalse);
  });

  testWidgets('Olvidé mi PIN pide confirmación y manda el PIN temporal por correo', (tester) async {
    await _abrir(tester, _sesion(tienePin: true), 'Cambiar PIN de compras');

    await tester.tap(find.text('Olvidé mi PIN'));
    await tester.pumpAndSettle();
    expect(find.textContaining('PIN temporal al correo'), findsOneWidget);
    await tester.tap(find.text('Enviar PIN temporal'));
    await tester.pumpAndSettle();

    expect(llamadas['/cuentas/pin/olvide']!['numeroCuenta'], '1234567890129604');
    expect(find.textContaining('Te enviamos un PIN temporal'), findsOneWidget);
  });

  testWidgets('sin PIN se configura directo, sin código por correo', (tester) async {
    await _abrir(tester, _sesion(tienePin: false), 'Configurar PIN de compras');

    expect(find.text('Paso 1 de 3'), findsNothing);
    await tester.enterText(find.widgetWithText(TextFormField, 'PIN nuevo'), '4321');
    await tester.enterText(find.widgetWithText(TextFormField, 'Confirma el PIN nuevo'), '4321');
    await tester.tap(find.text('Guardar PIN'));
    await tester.pumpAndSettle();

    final guardado = llamadas['/cuentas/pin']!;
    expect(guardado['pinNuevo'], '4321');
    expect(guardado.containsKey('pinActual'), isFalse);
    expect(llamadas.containsKey('/cuentas/pin/cambio/iniciar'), isFalse);
  });
}
