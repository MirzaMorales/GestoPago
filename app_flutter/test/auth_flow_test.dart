import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/screens/auth/auth_common.dart';
import 'package:gestopago_app/screens/welcome_screen.dart';
import 'package:gestopago_app/state/session.dart';
import 'package:intl/date_symbol_data_local.dart';

/// Monta el flujo de autenticación tal como aparece dentro del modal / pantalla.
Widget _app(Session s, ModoAuth modo) => MaterialApp(
      home: Scaffold(
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: FlujoAuth(session: s, inicial: modo),
        ),
      ),
    );

void main() {
  setUpAll(() => initializeDateFormatting('es_MX'));

  testWidgets('login valida campos vacíos sin llamar al servidor', (tester) async {
    await tester.pumpWidget(_app(Session(), ModoAuth.login));
    await tester.tap(find.text('Iniciar sesión'));
    await tester.pump();
    expect(find.text('Escribe tu correo'), findsOneWidget);
    expect(find.text('Escribe tu contraseña'), findsOneWidget);
  });

  testWidgets('desde login se cambia a crear cuenta y se regresa', (tester) async {
    await tester.binding.setSurfaceSize(const Size(900, 1400));
    await tester.pumpWidget(_app(Session(), ModoAuth.login));
    await tester.tap(find.text('Crear cuenta'));
    await tester.pumpAndSettle();
    expect(find.text('Crea tu cuenta'), findsOneWidget);
    expect(find.text('Paso 1 de 4 · Datos personales'), findsOneWidget);

    await tester.tap(find.text('Ya tengo cuenta'));
    await tester.pumpAndSettle();
    expect(find.text('Bienvenido de nuevo'), findsOneWidget);
  });

  testWidgets('registro no avanza con datos inválidos y avanza con datos válidos', (tester) async {
    await tester.binding.setSurfaceSize(const Size(900, 1600));
    await tester.pumpWidget(_app(Session(), ModoAuth.registro));

    await tester.tap(find.text('Continuar'));
    await tester.pump();
    expect(find.text('Paso 1 de 4 · Datos personales'), findsOneWidget);
    expect(find.text('Este dato es obligatorio'), findsWidgets);
    expect(find.text('Selecciona tu fecha de nacimiento'), findsOneWidget);

    Finder campo(String etiqueta) => find.widgetWithText(TextFormField, etiqueta);
    await tester.enterText(campo('Nombre'), 'Ana');
    await tester.enterText(campo('Apellido paterno'), 'López');
    await tester.enterText(campo('Apellido materno'), 'Ruiz');
    await tester.enterText(campo('CURP'), 'LORA000101MDFPZNA1');
    await tester.enterText(campo('RFC'), 'LORA000101AB1');

    // CURP inválida bloquea el avance
    await tester.enterText(campo('CURP'), 'MAL');
    await tester.tap(find.text('Continuar'));
    await tester.pump();
    expect(find.text('La CURP no tiene un formato válido'), findsOneWidget);
    await tester.enterText(campo('CURP'), 'LORA000101MDFPZNA1');

    // Fecha de nacimiento por el selector
    await tester.tap(campo('Fecha de nacimiento'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('OK').evaluate().isNotEmpty ? find.text('OK') : find.text('ACEPTAR'));
    await tester.pumpAndSettle();

    // Sexo y estado civil
    await tester.tap(find.text('Sexo'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Femenino').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Estado civil'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Soltero(a)').last);
    await tester.pumpAndSettle();

    await tester.tap(find.text('Continuar'));
    await tester.pumpAndSettle();
    expect(find.text('Paso 2 de 4 · Contacto'), findsOneWidget);
    // Los datos del paso anterior se conservan al regresar
    await tester.tap(find.text('Atrás'));
    await tester.pumpAndSettle();
    expect(find.text('Ana'), findsOneWidget);
  });

  testWidgets('las reglas de contraseña no aparecen antes del paso de acceso', (tester) async {
    await tester.binding.setSurfaceSize(const Size(900, 1600));
    await tester.pumpWidget(_app(Session(), ModoAuth.registro));
    expect(find.text('Mínimo 8 caracteres'), findsNothing);
  });

  testWidgets('bienvenida móvil muestra solo las dos opciones y abre el registro', (tester) async {
    await tester.binding.setSurfaceSize(const Size(400, 860));
    await tester.pumpWidget(MaterialApp(home: WelcomeScreen(session: Session())));
    expect(find.text('Iniciar sesión'), findsOneWidget);
    expect(find.text('Soy nuevo, crear cuenta'), findsOneWidget);
    expect(find.text('Todo lo que necesitas para pagar'), findsNothing); // sin landing en móvil

    await tester.tap(find.text('Soy nuevo, crear cuenta'));
    await tester.pumpAndSettle();
    expect(find.text('Crea tu cuenta'), findsOneWidget);
  });
}
