import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

/// URL del backend. Se puede cambiar al compilar: --dart-define=API_URL=http://localhost:8080
const String apiUrl = String.fromEnvironment(
  'API_URL',
  defaultValue: 'https://gestopago-bmb0.onrender.com',
);

/// Error devuelto por la API (formato GenericResponse: status, mensaje, detalles) o de red.
class ApiException implements Exception {
  ApiException(this.status, this.mensaje, [this.detalles = const {}]);

  /// Código HTTP; 0 cuando no hubo respuesta del servidor.
  final int status;
  final String mensaje;
  final Map<String, String> detalles;

  bool get sinConexion => status == 0;

  @override
  String toString() => mensaje;
}

/// Cliente HTTP único: agrega el JWT, decodifica el envoltorio y avisa cuando la sesión deja de ser válida.
class ApiClient {
  ApiClient({http.Client? httpClient}) : _http = httpClient ?? http.Client();

  final http.Client _http;
  String? token;

  /// Se llama cuando una petición autenticada recibe 401 (sesión expirada o cerrada desde otra plataforma).
  void Function()? onSesionInvalida;

  static const _timeout = Duration(seconds: 75); // Render puede tardar en despertar

  Future<dynamic> get(String path) => _enviar('GET', path);

  Future<dynamic> post(String path, [Object? body, Map<String, String>? headers]) =>
      _enviar('POST', path, body: body, headers: headers);

  Future<dynamic> put(String path, [Object? body]) => _enviar('PUT', path, body: body);

  Future<dynamic> _enviar(String metodo, String path,
      {Object? body, Map<String, String>? headers}) async {
    final uri = Uri.parse('$apiUrl$path');
    final conToken = token != null;
    final req = http.Request(metodo, uri)
      ..headers.addAll({
        'Accept': 'application/json',
        if (body != null) 'Content-Type': 'application/json; charset=utf-8',
        if (conToken) 'Authorization': 'Bearer $token',
        ...?headers,
      });
    if (body != null) req.body = jsonEncode(body);

    http.Response res;
    try {
      res = await http.Response.fromStream(await _http.send(req).timeout(_timeout));
    } on TimeoutException {
      throw ApiException(0, 'El servidor tardó demasiado en responder. Intenta de nuevo.');
    } catch (_) {
      throw ApiException(0, 'No hay conexión con el servidor. Revisa tu internet.');
    }

    final dynamic json = _decodificar(res);
    if (res.statusCode >= 200 && res.statusCode < 300) return json;

    if (res.statusCode == 401 && conToken) {
      onSesionInvalida?.call();
    }
    throw _error(res.statusCode, json);
  }

  dynamic _decodificar(http.Response res) {
    if (res.bodyBytes.isEmpty) return null;
    try {
      return jsonDecode(utf8.decode(res.bodyBytes));
    } catch (_) {
      return null;
    }
  }

  ApiException _error(int status, dynamic json) {
    String mensaje = switch (status) {
      401 => 'Tu sesión no es válida. Inicia sesión nuevamente.',
      403 => 'No tienes permiso para realizar esta acción.',
      404 => 'No se encontró la información solicitada.',
      429 => 'Demasiados intentos. Espera unos minutos.',
      >= 500 => 'El servicio no está disponible en este momento.',
      _ => 'No se pudo completar la solicitud.',
    };
    final detalles = <String, String>{};
    if (json is Map) {
      final m = json['mensaje'];
      if (m is String && m.isNotEmpty) mensaje = m;
      final d = json['detalles'];
      if (d is Map) {
        d.forEach((k, v) => detalles['$k'] = '$v');
      }
    }
    return ApiException(status, mensaje, detalles);
  }
}
