import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

const _semilla = Color(0xFF0F766E); // verde azulado

ThemeData construirTema(Brightness brillo) {
  final esquema = ColorScheme.fromSeed(seedColor: _semilla, brightness: brillo);
  return ThemeData(
    useMaterial3: true,
    colorScheme: esquema,
    scaffoldBackgroundColor:
        brillo == Brightness.light ? const Color(0xFFF6F8F8) : esquema.surface,
    appBarTheme: AppBarTheme(
      centerTitle: false,
      backgroundColor: Colors.transparent,
      surfaceTintColor: Colors.transparent,
      titleTextStyle: TextStyle(
          fontSize: 22, fontWeight: FontWeight.w700, color: esquema.onSurface),
    ),
    cardTheme: CardThemeData(
      elevation: 0,
      color: esquema.surfaceContainerLowest,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: esquema.outlineVariant.withValues(alpha: 0.6)),
      ),
      margin: EdgeInsets.zero,
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: esquema.surfaceContainerLowest,
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size(0, 52),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size(0, 52),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    ),
  );
}

final _moneda = NumberFormat.currency(locale: 'es_MX', symbol: r'$', decimalDigits: 2);
final _fechaHora = DateFormat("d MMM yyyy, HH:mm", 'es_MX');

String dinero(num? v) => v == null ? '—' : _moneda.format(v);

String fechaHora(DateTime? f) => f == null ? '—' : _fechaHora.format(f);

/// Colores semánticos para el resultado de una compra.
class Estados {
  static const exito = Color(0xFF15803D);
  static const rechazo = Color(0xFFB91C1C);
  static const pendiente = Color(0xFFB45309);
  static const error = Color(0xFF475569);
}
