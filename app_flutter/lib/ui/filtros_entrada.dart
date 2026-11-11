import 'package:flutter/services.dart';

/// Filtros de captura equivalentes a las listas blancas del backend (PatronesSeguros).
/// Impiden escribir HTML, scripts o caracteres de control; el servidor vuelve a validar todo.
class FiltrosEntrada {
  FiltrosEntrada._();

  /// Letras con acentos, números, espacios y . , ' ’ - # / & ( ) ° º ª
  static final textoLibre = FilteringTextInputFormatter.allow(
      RegExp(r"[\p{L}\p{M}\p{N} .,'’\-#/&()°ºª]", unicode: true));

  /// Letras, espacios y . ' ’ - (nombres y apellidos).
  static final soloLetras =
      FilteringTextInputFormatter.allow(RegExp(r"[\p{L}\p{M} .'’\-]", unicode: true));

  /// Caracteres válidos en un correo común (sin espacios, comillas ni signos de HTML).
  static final correo = FilteringTextInputFormatter.allow(RegExp(r'[A-Za-z0-9._%+@\-]'));

  /// Contraseñas: sin caracteres de control (las admite largas hasta 128).
  static final sinControl = FilteringTextInputFormatter.deny(RegExp(r'[\x00-\x1F\x7F]'));

  static const maxPassword = 128;
}
