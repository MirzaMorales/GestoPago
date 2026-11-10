import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../api/api_client.dart';
import '../api/gestopago_api.dart';
import '../api/models.dart';

/// Estado de sesión de la app. El servidor es quien decide si la sesión sigue vigente:
/// ante un 401 se borra el token local y se vuelve al login.
class Session extends ChangeNotifier {
  Session() {
    client.onSesionInvalida = () => _expirar(
        'Tu sesión terminó: expiró o se inició sesión desde otro dispositivo.');
  }

  final ApiClient client = ApiClient();
  late final GestoPagoApi api = GestoPagoApi(client);

  static const _storage = FlutterSecureStorage();
  static const _clave = 'gp_sesion';

  Usuario? usuario;
  bool cargando = true;

  /// Mensaje que se muestra una vez en el login (sesión expirada, cerrada, etc.).
  String? aviso;

  bool get autenticado => usuario != null;

  /// WEB o MOVIL, para que el servidor sepa dónde está la sesión activa.
  String get plataforma => kIsWeb ? 'WEB' : 'MOVIL';

  Future<void> restaurar() async {
    try {
      final guardado = await _storage.read(key: _clave);
      if (guardado != null) {
        final u = Usuario.fromJson(Map<String, dynamic>.from(jsonDecode(guardado) as Map));
        if (u.token.isNotEmpty) {
          usuario = u;
          client.token = u.token;
        }
      }
    } catch (_) {
      await _storage.delete(key: _clave);
    }
    cargando = false;
    notifyListeners();
  }

  Future<void> iniciar(Usuario u) async {
    usuario = u;
    client.token = u.token;
    aviso = null;
    await _storage.write(key: _clave, value: jsonEncode(u.toJson()));
    notifyListeners();
  }

  Future<void> cerrarSesion() async {
    try {
      if (client.token != null) await api.logout();
    } catch (_) {
      // Aunque falle la red se limpia localmente; la sesión del servidor expira sola.
    }
    await _limpiar();
    aviso = 'Cerraste sesión correctamente.';
    notifyListeners();
  }

  void _expirar(String mensaje) {
    if (usuario == null) return;
    _limpiar();
    aviso = mensaje;
    notifyListeners();
  }

  Future<void> _limpiar() async {
    usuario = null;
    client.token = null;
    await _storage.delete(key: _clave);
  }
}
