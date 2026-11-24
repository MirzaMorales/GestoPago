import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/api/api_client.dart';
import 'package:gestopago_app/api/models.dart';
import 'package:gestopago_app/screens/purchase_result_screen.dart';
import 'package:gestopago_app/state/session.dart';
import 'package:gestopago_app/ui/tarjeta_cuenta.dart';
import 'package:intl/date_symbol_data_local.dart';

final _producto = Producto.fromJson({
  'idProducto': 185,
  'producto': 'Agua Cancun',
  'idServicio': 56,
  'servicio': 'AGUAKAN (Cancun)',
  'tipoFront': 2,
  'categoria': 'Pago de servicios',
  'montoLibre': true,
  'disponibleParaCompra': true,
});

ResultadoCompra _rechazo() => ResultadoCompra.desdeError(ApiException(422, 'GestoPago rechazó la transacción', {
      'codigo': '1101',
      'error': 'GESTOPAGO_RECHAZADO',
      'estadoCompra': 'RECHAZADA',
      'idTransaccion': '2',
      'codigoProveedor': '55',
      'mensajeProveedor': 'Origen Valido',
    }));

ResultadoCompra _exito() => ResultadoCompra.exitosa(ResultadoPago.fromJson({
      'idTransaccion': 7,
      'monto': 250,
      'saldoRestante': 750,
      'producto': 'Agua Cancun',
      'referencia': '25720381',
      'idTxGestoPago': '291620941',
      'numAutorizacion': '100074211',
    }));

/// Abre la pantalla de resultado desde un botón y devuelve lo que el usuario eligió al salir.
Future<AccionResultado? Function()> _abrir(WidgetTester tester, ResultadoCompra resultado,
    {double ancho = 620}) async {
  AccionResultado? elegida;
  _tamano(tester, ancho);
  await tester.pumpWidget(MaterialApp(
    home: Builder(
      builder: (ctx) => Scaffold(
        body: Center(
          child: ElevatedButton(
            onPressed: () async {
              elegida = await Navigator.of(ctx).push<AccionResultado>(MaterialPageRoute(
                builder: (_) => PurchaseResultScreen(
                  session: Session(),
                  producto: _producto,
                  importe: 5,
                  resultado: resultado,
                  referencia: '1223100813',
                  cuenta: '•••• 9604',
                ),
              ));
            },
            child: const Text('abrir'),
          ),
        ),
      ),
    ),
  ));
  return () => elegida;
}

/// Tamaño lógico real: en las pruebas la densidad por defecto es 3 y el ancho se dividiría entre 3.
void _tamano(WidgetTester tester, double ancho, [double alto = 900]) {
  tester.view.devicePixelRatio = 1.0;
  tester.view.physicalSize = Size(ancho, alto);
  addTearDown(tester.view.reset);
}

void main() {
  setUpAll(() => initializeDateFormatting('es_MX'));

  group('tarjeta de la cuenta', () {
    final cuenta = Cuenta('1234567890129604', 1250.5, 'ACTIVA');

    for (final ancho in [300.0, 360.0, 440.0]) {
      testWidgets('se ve completa sin desbordes con $ancho px de ancho', (tester) async {
        _tamano(tester, ancho + 32, 700);
        await tester.pumpWidget(MaterialApp(
          home: Scaffold(body: Padding(padding: const EdgeInsets.all(16), child: TarjetaCuenta(cuenta: cuenta, titular: 'Ana López Ruiz'))),
        ));
        expect(tester.takeException(), isNull);
        expect(find.text('GestoPago'), findsOneWidget);
        expect(find.text('ANA LÓPEZ RUIZ'), findsOneWidget);
        expect(find.text('SALDO DISPONIBLE'), findsOneWidget);
      });
    }

    testWidgets('el número va oculto y el ojo lo muestra completo', (tester) async {
      _tamano(tester, 500, 700);
      await tester.pumpWidget(MaterialApp(home: Scaffold(body: TarjetaCuenta(cuenta: cuenta, titular: 'Ana'))));

      expect(find.text('•••• •••• •••• 9604'), findsOneWidget);
      expect(find.text('1234 5678 9012 9604'), findsNothing);

      await tester.tap(find.byTooltip('Ver número completo'));
      await tester.pump();
      expect(find.text('1234 5678 9012 9604'), findsOneWidget);
      expect(find.byTooltip('Copiar número'), findsOneWidget);

      await tester.tap(find.byTooltip('Ocultar número'));
      await tester.pump();
      expect(find.text('•••• •••• •••• 9604'), findsOneWidget);
    });

    testWidgets('una cuenta no activa lo indica en la tarjeta', (tester) async {
      _tamano(tester, 500, 700);
      await tester.pumpWidget(MaterialApp(
          home: Scaffold(body: TarjetaCuenta(cuenta: Cuenta('1234567890129604', 0, 'BLOQUEADA')))));
      expect(find.text('BLOQUEADA'), findsOneWidget);
      expect(find.text('CUENTA DIGITAL'), findsOneWidget); // sin titular
    });
  });

  group('salidas de la pantalla de resultado', () {
    testWidgets('rechazada: "Intentar de nuevo" regresa al producto', (tester) async {
      final elegida = await _abrir(tester, _rechazo());
      await tester.tap(find.text('abrir'));
      await tester.pumpAndSettle();

      expect(find.text('Intentar de nuevo'), findsOneWidget);
      await tester.tap(find.text('Intentar de nuevo'));
      await tester.pumpAndSettle();
      expect(elegida(), AccionResultado.reintentar);
    });

    testWidgets('rechazada: la flecha de volver también regresa al producto', (tester) async {
      final elegida = await _abrir(tester, _rechazo());
      await tester.tap(find.text('abrir'));
      await tester.pumpAndSettle();

      await tester.tap(find.byTooltip('Volver al producto'));
      await tester.pumpAndSettle();
      expect(elegida(), AccionResultado.reintentar);
    });

    testWidgets('rechazada: puede ir a Mis compras o al catálogo', (tester) async {
      var elegida = await _abrir(tester, _rechazo());
      await tester.tap(find.text('abrir'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Mis compras'));
      await tester.pumpAndSettle();
      expect(elegida(), AccionResultado.verCompras);

      elegida = await _abrir(tester, _rechazo());
      await tester.tap(find.text('abrir'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Catálogo'));
      await tester.pumpAndSettle();
      expect(elegida(), AccionResultado.catalogo);
    });

    testWidgets('exitosa: Ver mis compras, Seguir comprando y flecha al catálogo', (tester) async {
      var elegida = await _abrir(tester, _exito());
      await tester.tap(find.text('abrir'));
      await tester.pumpAndSettle();
      expect(find.text('Compra exitosa'), findsOneWidget);
      await tester.tap(find.text('Ver mis compras'));
      await tester.pumpAndSettle();
      expect(elegida(), AccionResultado.verCompras);

      elegida = await _abrir(tester, _exito());
      await tester.tap(find.text('abrir'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Seguir comprando'));
      await tester.pumpAndSettle();
      expect(elegida(), AccionResultado.catalogo);

      elegida = await _abrir(tester, _exito());
      await tester.tap(find.text('abrir'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Volver al catálogo'));
      await tester.pumpAndSettle();
      expect(elegida(), AccionResultado.catalogo);
    });

    testWidgets('en pantalla ancha usa dos columnas con las acciones junto al resultado', (tester) async {
      await _abrir(tester, _rechazo(), ancho: 1200);
      await tester.tap(find.text('abrir'));
      await tester.pumpAndSettle();

      expect(find.text('Resumen de la compra'), findsOneWidget);
      expect(find.text('Ver mis compras'), findsOneWidget); // texto largo: pantalla ancha
      expect(find.text('Ir al catálogo'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  });
}
