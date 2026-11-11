import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/api/api_client.dart';
import 'package:gestopago_app/api/models.dart';
import 'package:gestopago_app/screens/admin/clientes_admin_screen.dart';
import 'package:gestopago_app/screens/admin/pendientes_admin_screen.dart';
import 'package:gestopago_app/screens/auth/auth_common.dart';
import 'package:gestopago_app/state/session.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:intl/date_symbol_data_local.dart';

/// Peticiones recibidas por el servidor simulado (ruta → cuerpo).
final llamadas = <String, Map<String, dynamic>>{};

http.Response _json(Object datos, [int status = 200]) => http.Response(jsonEncode(datos), status,
    headers: {'content-type': 'application/json; charset=utf-8'});

final _cliente = {
  'id': 10,
  'nombre': 'Ana',
  'apellidoPaterno': 'López',
  'apellidoMaterno': 'Ruiz',
  'fechaNacimiento': '1995-05-20',
  'curp': 'LORA950520MDFPZN01',
  'rfc': 'LORA950520AB1',
  'correo': 'ana@example.com',
  'telefonoMovil': '5512345678',
  'ocupacion': 'Ingeniera',
  'empresa': 'ACME',
  'activo': true,
  'domicilio': {'calle': 'Centro', 'numeroExterior': '1', 'colonia': 'Centro', 'municipio': 'San Miguel de Allende',
    'estado': 'Guanajuato', 'codigoPostal': '37700'},
  'cuentas': [
    {'numeroCuenta': '1234567890123456', 'saldo': 100, 'estatus': 'ACTIVA'}
  ],
  'usuario': {'id': 1},
};

Session _sesion() {
  final http = MockClient((req) async {
    final cuerpo = req.body.isEmpty ? <String, dynamic>{} : jsonDecode(req.body) as Map<String, dynamic>;
    llamadas[req.url.path] = cuerpo;
    switch (req.url.path) {
      case '/auth/recuperar/solicitar':
        return _json({'requiere2fa': true, 'desafioId': 'd-1', 'correoEnmascarado': 'a***@example.com', 'expiraEnSegundos': 300});
      case '/auth/recuperar/restablecer':
        return _json({'status': 200, 'mensaje': 'ok'});
      case '/clientes/buscar':
        return _json({'status': 200, 'datos': [_cliente]});
      case '/cuentas/abono':
        return _json({'status': 200, 'datos': {'saldo': 100 + (cuerpo['monto'] as num)}});
      case '/pagos/conciliacion/pendientes':
        return _json({'status': 200, 'datos': [
          {'id': 77, 'numeroCuenta': '1234567890123456', 'idProducto': 582, 'idServicio': 133, 'monto': 50,
            'estatus': 'EN_REVISION', 'upc': 'ABC', 'fechaEnvio': '2026-11-10T10:00:00-06:00', 'detalle': 'timeout',
            'requiereResolucionManual': true}
        ]});
      case '/pagos/conciliacion/resolver':
        return _json({'status': 200, 'datos': {'resultado': 'REVERTIDA'}});
    }
    return _json({'status': 404, 'mensaje': 'no existe'}, 404);
  });
  return Session(client: ApiClient(httpClient: http));
}

Future<Session> _sesionAdmin() async {
  final s = _sesion();
  await s.iniciar(Usuario(token: 't', correo: 'admin@example.com', usuarioId: 2, clienteId: null, rol: 'ADMIN'));
  return s;
}

Widget _app(Widget hijo) => MaterialApp(home: hijo);

void main() {
  setUpAll(() => initializeDateFormatting('es_MX'));
  setUp(() {
    FlutterSecureStorage.setMockInitialValues({});
    llamadas.clear();
  });

  testWidgets('olvidé mi contraseña: correo → código y contraseña nueva → regresa al login', (tester) async {
    await tester.binding.setSurfaceSize(const Size(700, 1400));
    await tester.pumpWidget(_app(Scaffold(body: SingleChildScrollView(
        child: FlujoAuth(session: _sesion(), inicial: ModoAuth.login)))));

    await tester.tap(find.text('¿Olvidaste tu contraseña?'));
    await tester.pumpAndSettle();
    expect(find.text('Recupera tu contraseña'), findsOneWidget);

    await tester.enterText(find.widgetWithText(TextFormField, 'Correo electrónico'), 'ana@example.com');
    await tester.tap(find.text('Enviar código'));
    await tester.pumpAndSettle();
    expect(find.text('Crea tu nueva contraseña'), findsOneWidget);
    expect(llamadas['/auth/recuperar/solicitar'], {'correo': 'ana@example.com'});

    await tester.enterText(find.widgetWithText(TextFormField, 'Código'), '123456');
    await tester.enterText(find.widgetWithText(TextFormField, 'Contraseña nueva'), 'debil');
    await tester.pump();
    expect(find.text('La contraseña no cumple las reglas'), findsOneWidget); // validación en tiempo real

    await tester.enterText(find.widgetWithText(TextFormField, 'Contraseña nueva'), 'Nueva#Clave9');
    await tester.enterText(find.widgetWithText(TextFormField, 'Confirma la contraseña nueva'), 'Nueva#Clave9');
    await tester.tap(find.text('Guardar contraseña'));
    await tester.pumpAndSettle();

    expect(llamadas['/auth/recuperar/restablecer'],
        {'desafioId': 'd-1', 'codigo': '123456', 'nuevaPassword': 'Nueva#Clave9'});
    expect(find.text('Bienvenido de nuevo'), findsOneWidget);
    expect(find.textContaining('Tu contraseña se actualizó'), findsOneWidget);
  });

  testWidgets('registro: nacionalidad fija y errores en tiempo real sin pulsar Continuar', (tester) async {
    await tester.binding.setSurfaceSize(const Size(900, 1600));
    await tester.pumpWidget(_app(Scaffold(body: SingleChildScrollView(
        child: FlujoAuth(session: _sesion(), inicial: ModoAuth.registro)))));

    final nacionalidad = tester.widget<TextFormField>(find.widgetWithText(TextFormField, 'Nacionalidad'));
    expect(nacionalidad.enabled, isFalse);
    expect(find.text('Mexicana'), findsOneWidget);

    await tester.enterText(find.widgetWithText(TextFormField, 'CURP'), 'ABC');
    await tester.pump();
    expect(find.text('La CURP no tiene un formato válido'), findsOneWidget);
  });

  testWidgets('ADMIN abona saldo con tope y confirmación', (tester) async {
    await tester.binding.setSurfaceSize(const Size(900, 1400));
    final s = await _sesionAdmin();
    await tester.pumpWidget(_app(ClienteAdminDetalleScreen(session: s, clienteId: 10)));
    await tester.pumpAndSettle();
    expect(find.text('Ana López Ruiz'), findsOneWidget);

    await tester.tap(find.text('Abonar'));
    await tester.pumpAndSettle();
    await tester.enterText(find.widgetWithText(TextFormField, 'Monto a abonar'), '60000');
    await tester.pump();
    expect(find.textContaining('El máximo por abono'), findsOneWidget);

    await tester.enterText(find.widgetWithText(TextFormField, 'Monto a abonar'), '500');
    await tester.tap(find.text('Continuar'));
    await tester.pumpAndSettle();
    expect(find.text('Confirma el abono'), findsOneWidget);
    await tester.tap(find.textContaining('Abonar \$'));
    await tester.pumpAndSettle();

    expect(llamadas['/cuentas/abono'], {'numeroCuenta': '1234567890123456', 'monto': 500.0});
    expect(find.textContaining('Abono aplicado'), findsOneWidget);
  });

  testWidgets('ADMIN busca clientes por correo', (tester) async {
    await tester.binding.setSurfaceSize(const Size(900, 1200));
    final s = await _sesionAdmin();
    await tester.pumpWidget(_app(ClientesAdminScreen(session: s)));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField).first, 'ana@example.com');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();

    expect(llamadas['/clientes/buscar']?['correo'], 'ana@example.com');
    expect(find.text('Ana López Ruiz'), findsOneWidget);
  });

  testWidgets('ADMIN revierte una compra pendiente con observación obligatoria', (tester) async {
    await tester.binding.setSurfaceSize(const Size(900, 1400));
    final s = await _sesionAdmin();
    await tester.pumpWidget(_app(PendientesAdminScreen(session: s)));
    await tester.pumpAndSettle();
    expect(find.text('Folio 77 · \$50.00'), findsOneWidget);

    await tester.tap(find.text('Revertir y devolver saldo'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Revertir'));
    await tester.pump();
    expect(find.text('Escribe el motivo de la resolución'), findsOneWidget);

    await tester.enterText(find.widgetWithText(TextFormField, 'Observación'), 'Proveedor no la aplico');
    await tester.tap(find.widgetWithText(FilledButton, 'Revertir'));
    await tester.pumpAndSettle();
    expect(llamadas['/pagos/conciliacion/resolver'],
        {'idTransaccion': 77, 'accion': 'REVERTIR', 'observacion': 'Proveedor no la aplico'});
  });
}
