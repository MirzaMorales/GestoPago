import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'screens/home_shell.dart';
import 'screens/welcome_screen.dart';
import 'state/session.dart';
import 'ui/theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('es_MX');
  final session = Session();
  await session.restaurar();
  runApp(GestoPagoApp(session: session));
}

class GestoPagoApp extends StatelessWidget {
  const GestoPagoApp({super.key, required this.session});

  final Session session;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: session,
      builder: (context, _) => MaterialApp(
        // Al cambiar de sesión se reconstruye el navegador completo: no quedan pantallas protegidas abiertas.
        key: ValueKey(session.usuario?.token ?? 'sin-sesion'),
        title: 'GestoPago',
        debugShowCheckedModeBanner: false,
        theme: construirTema(Brightness.light),
        darkTheme: construirTema(Brightness.dark),
        locale: const Locale('es', 'MX'),
        supportedLocales: const [Locale('es', 'MX'), Locale('es')],
        localizationsDelegates: const [
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        home: session.autenticado
            ? HomeShell(session: session)
            : WelcomeScreen(session: session),
      ),
    );
  }
}
