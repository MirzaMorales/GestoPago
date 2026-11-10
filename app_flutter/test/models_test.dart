import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/api/api_client.dart';
import 'package:gestopago_app/api/models.dart';
import 'package:gestopago_app/screens/purchase_result_screen.dart';

void main() {
  group('Producto', () {
    test('recarga con precio fijo pide teléfono', () {
      final p = Producto.fromJson({
        'idProducto': 582,
        'producto': 'Telcel \$50',
        'idServicio': 133,
        'servicio': 'Telcel',
        'idCatTipoServicio': 1,
        'tipoFront': 1,
        'precio': 50.0,
        'categoria': 'Recargas y tarjetas',
        'disponibleParaCompra': true,
        'montoLibre': false,
      });
      expect(p.pideTelefono, isTrue);
      expect(p.montoLibre, isFalse);
      expect(p.precio, 50.0);
    });

    test('pago de servicio es monto libre y no pide teléfono', () {
      final p = Producto.fromJson({'idProducto': 205, 'idServicio': 76, 'tipoFront': 2});
      expect(p.montoLibre, isTrue);
      expect(p.pideTelefono, isFalse);
      expect(p.disponibleParaCompra, isTrue);
    });

    test('tarjeta canjeable no pide teléfono', () {
      final p = Producto.fromJson({'idProducto': 200, 'tipoFront': 1, 'precio': 100, 'idCatTipoServicio': 10});
      expect(p.esCanjeable, isTrue);
      expect(p.pideTelefono, isFalse);
    });
  });

  group('ResultadoCompra.desdeError', () {
    test('usa estadoCompra que envía el backend', () {
      final r = ResultadoCompra.desdeError(ApiException(504, 'GestoPago no respondió',
          {'estadoCompra': 'PENDIENTE', 'idTransaccion': '15'}));
      expect(r.estado, EstadoCompra.pendiente);
      expect(r.idTransaccion, 15);
    });

    test('sin conexión es error de comunicación', () {
      expect(ResultadoCompra.desdeError(ApiException(0, 'sin red')).estado,
          EstadoCompra.errorComunicacion);
    });

    test('validación 400 sin estado es rechazada', () {
      expect(ResultadoCompra.desdeError(ApiException(400, 'Error de validación')).estado,
          EstadoCompra.rechazada);
    });
  });

  test('Compra lee fecha ISO con zona horaria', () {
    final c = Compra.fromJson({
      'idTransaccion': 7,
      'fecha': '2026-11-10T14:30:00-06:00',
      'monto': 50,
      'producto': 'Telcel',
    });
    expect(c.fecha, isNotNull);
    expect(c.monto, 50);
  });
}
