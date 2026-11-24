import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/api/api_client.dart';
import 'package:gestopago_app/api/models.dart';
import 'package:gestopago_app/screens/purchase_result_screen.dart';
import 'package:gestopago_app/state/session.dart';
import 'package:intl/date_symbol_data_local.dart';

ApiException _rechazo55() => ApiException(422, 'GestoPago rechazó la transacción', {
      'codigo': '1101',
      'error': 'GESTOPAGO_RECHAZADO',
      'estadoCompra': 'RECHAZADA',
      'detalle': 'Código de GestoPago: 55',
      'idTransaccion': '41',
      'codigoProveedor': '55',
      'mensajeProveedor': 'Referencia no encontrada',
      'sugerencia': 'Verifica la referencia y el importe de tu recibo.',
    });

final _agua = Producto.fromJson({
  'idProducto': 185,
  'producto': 'Agua Cancun',
  'idServicio': 56,
  'servicio': 'AGUAKAN (Cancun)',
  'tipoFront': 2,
  'categoria': 'Pago de servicios',
  'montoLibre': true,
  'disponibleParaCompra': true,
});

void main() {
  setUpAll(() => initializeDateFormatting('es_MX'));

  test('el rechazo del proveedor conserva código, mensaje, folio y sugerencia', () {
    final r = ResultadoCompra.desdeError(_rechazo55());
    expect(r.estado, EstadoCompra.rechazada);
    expect(r.codigoProveedor, '55');
    expect(r.mensajeProveedor, 'Referencia no encontrada');
    expect(r.codigoError, '1101');
    expect(r.error, 'GESTOPAGO_RECHAZADO');
    expect(r.idTransaccion, 41);
    expect(r.sugerencia, contains('referencia'));
  });

  test('un error de validación muestra el motivo del campo', () {
    final r = ResultadoCompra.desdeError(
        ApiException(400, 'Error de validación en la solicitud', {'pin': 'El PIN debe contener exactamente 4 dígitos'}));
    expect(r.detalle, 'El PIN debe contener exactamente 4 dígitos');
    expect(r.codigoError, '400');
  });

  testWidgets('la pantalla de rechazo muestra motivo, códigos, folio, cargo y qué hacer', (tester) async {
    await tester.binding.setSurfaceSize(const Size(700, 1600));
    await tester.pumpWidget(MaterialApp(
      home: PurchaseResultScreen(
        session: Session(),
        producto: _agua,
        importe: 5,
        resultado: ResultadoCompra.desdeError(_rechazo55()),
        referencia: '25720381',
        cuenta: '•••• 9604',
      ),
    ));

    expect(find.text('Compra rechazada'), findsOneWidget);
    expect(find.text('Referencia no encontrada'), findsOneWidget);
    expect(find.text('55'), findsOneWidget);
    expect(find.text('1101 · GESTOPAGO_RECHAZADO'), findsOneWidget);
    expect(find.text('41'), findsOneWidget);
    expect(find.text('25720381'), findsOneWidget);
    expect(find.text('•••• 9604'), findsOneWidget);
    expect(find.text('Ninguno: tu saldo no cambió'), findsOneWidget);
    expect(find.text('Verifica la referencia y el importe de tu recibo.'), findsOneWidget);
    expect(find.text('Copiar detalle para soporte'), findsOneWidget);
  });
}
