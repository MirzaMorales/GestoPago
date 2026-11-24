import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/api/api_client.dart';
import 'package:gestopago_app/screens/history_screen.dart';
import 'package:gestopago_app/state/session.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:intl/date_symbol_data_local.dart';

final peticiones = <Map<String, dynamic>>[];

Map<String, dynamic> _compra(int id, String estado, String motivo) => {
      'idTransaccion': id,
      'fecha': '2026-11-23T18:10:00-06:00',
      'idProducto': 185,
      'idServicio': 56,
      'producto': 'Agua Cancun $id',
      'referencia': '1223100813',
      'monto': 5,
      'cuenta': '************9604',
      'estatus': estado == 'RECHAZADA' ? 'FALLIDA' : estado,
      'estadoCompra': estado,
      'motivo': motivo,
      'numAutorizacion': estado == 'EXITOSA' ? 'AUT-1' : '',
      'idTxGestoPago': estado == 'EXITOSA' ? 'GP-1' : '',
    };

final _todas = [
  _compra(3, 'EXITOSA', ''),
  _compra(2, 'RECHAZADA', 'GestoPago rechazó la compra (código 55). No se hizo ningún cargo'),
  _compra(1, 'PENDIENTE', 'GestoPago aún no confirma el resultado; el importe está retenido hasta confirmarlo'),
];

Session _sesion() => Session(
      client: ApiClient(
        httpClient: MockClient((req) async {
          final cuerpo = jsonDecode(req.body.isEmpty ? '{}' : req.body) as Map<String, dynamic>;
          dynamic datos;
          if (req.url.path == '/pagos/historial') {
            peticiones.add(cuerpo);
            final estado = cuerpo['estado'];
            final lista = _todas.where((c) => estado == null || c['estadoCompra'] == estado).toList();
            datos = {'contenido': lista, 'pagina': 0, 'tamano': 20, 'totalElementos': lista.length, 'totalPaginas': 1};
          } else if (req.url.path == '/pagos/historial/detalle') {
            datos = _todas.firstWhere((c) => c['idTransaccion'] == cuerpo['idTransaccion']);
          }
          return http.Response(jsonEncode({'status': 200, 'datos': datos}), 200,
              headers: {'content-type': 'application/json; charset=utf-8'});
        }),
      ),
    );

void main() {
  setUpAll(() => initializeDateFormatting('es_MX'));
  setUp(() {
    FlutterSecureStorage.setMockInitialValues({});
    peticiones.clear();
  });

  testWidgets('muestra compras exitosas, rechazadas y pendientes', (tester) async {
    await tester.binding.setSurfaceSize(const Size(800, 1200));
    await tester.pumpWidget(MaterialApp(home: HistoryScreen(session: _sesion())));
    await tester.pumpAndSettle();

    expect(find.text('3 compras'), findsOneWidget);
    expect(find.text('Exitosa'), findsOneWidget);
    expect(find.text('Rechazada'), findsOneWidget);
    expect(find.text('Pendiente'), findsOneWidget);
    expect(peticiones.single.containsKey('estado'), isFalse); // sin filtro = todas
  });

  testWidgets('el filtro de estado se envía al servidor y muestra solo esas compras', (tester) async {
    await tester.binding.setSurfaceSize(const Size(800, 1200));
    await tester.pumpWidget(MaterialApp(home: HistoryScreen(session: _sesion())));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Rechazadas'));
    await tester.pumpAndSettle();

    expect(peticiones.last['estado'], 'RECHAZADA');
    expect(find.text('1 compra'), findsOneWidget);
    expect(find.text('Rechazada'), findsOneWidget);
    expect(find.text('Exitosa'), findsNothing);
  });

  testWidgets('el detalle de una compra rechazada explica el motivo y que no hubo cargo', (tester) async {
    await tester.binding.setSurfaceSize(const Size(800, 1400));
    await tester.pumpWidget(MaterialApp(home: HistoryScreen(session: _sesion())));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Agua Cancun 2'));
    await tester.pumpAndSettle();

    expect(find.text('Detalle de la compra'), findsOneWidget);
    expect(find.text('GestoPago rechazó la compra (código 55). No se hizo ningún cargo'), findsOneWidget);
    expect(find.text('Ninguno'), findsOneWidget);
    expect(find.text('Autorización'), findsNothing);
  });
}
