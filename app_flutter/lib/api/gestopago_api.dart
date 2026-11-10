import 'dart:math';

import 'api_client.dart';
import 'models.dart';

/// Endpoints del backend usados por la app (web y móvil comparten exactamente los mismos).
class GestoPagoApi {
  GestoPagoApi(this.client);

  final ApiClient client;

  Map<String, dynamic> _datos(dynamic json) =>
      (json is Map && json['datos'] is Map) ? Map<String, dynamic>.from(json['datos'] as Map) : {};

  // ---------- Autenticación ----------

  /// Paso 1. Lanza ApiException 409 con detalles.error = SESION_ACTIVA si hay otra sesión abierta.
  Future<Desafio2fa> login(String correo, String password,
      {required String plataforma, bool cerrarSesionAnterior = false}) async {
    final json = await client.post('/auth/login', {
      'correo': correo,
      'password': password,
      'plataforma': plataforma,
      'cerrarSesionAnterior': cerrarSesionAnterior,
    });
    return Desafio2fa.fromJson(Map<String, dynamic>.from(json as Map));
  }

  /// Paso 2. El código se valida en el servidor; devuelve el JWT.
  Future<Usuario> verificar2fa(String desafioId, String codigo) async {
    final json = await client.post('/auth/verificar-2fa', {'desafioId': desafioId, 'codigo': codigo});
    return Usuario.fromJson(Map<String, dynamic>.from(json as Map));
  }

  Future<void> logout() => client.post('/auth/logout');

  /// Alta pública de cliente: crea cliente, usuario CLIENTE y su cuenta. Devuelve el número de cuenta creado.
  Future<String?> registrar(Map<String, dynamic> datos) async {
    final json = await client.post('/clientes', datos);
    final cuentas = _datos(json)['cuentas'];
    if (cuentas is List && cuentas.isNotEmpty && cuentas.first is Map) {
      return '${(cuentas.first as Map)['numeroCuenta'] ?? ''}';
    }
    return null;
  }

  // ---------- Catálogo de domicilios (SEPOMEX, público) ----------

  List<dynamic> _lista(dynamic json) => (json is Map && json['datos'] is List) ? json['datos'] as List : const [];

  Future<List<Colonia>> coloniasPorCp(String cp) async => _lista(await client.get('/catalogos/codigos-postales/$cp'))
      .whereType<Map<String, dynamic>>()
      .map(Colonia.fromJson)
      .toList();

  Future<List<String>> estados() async =>
      _lista(await client.get('/catalogos/estados')).map((e) => '$e').toList();

  Future<List<String>> municipios(String estado) async => _lista(
          await client.get('/catalogos/municipios?estado=${Uri.encodeQueryComponent(estado)}'))
      .map((e) => '$e')
      .toList();

  Future<List<Colonia>> colonias(String estado, String municipio) async => _lista(await client.get(
          '/catalogos/colonias?estado=${Uri.encodeQueryComponent(estado)}'
          '&municipio=${Uri.encodeQueryComponent(municipio)}'))
      .whereType<Map<String, dynamic>>()
      .map(Colonia.fromJson)
      .toList();

  // ---------- Catálogo ----------

  Future<List<Producto>> productos() async {
    final json = await client.get('/productos/almacenados');
    final lista = (json is Map ? json['productos'] : null) as List? ?? [];
    return lista.whereType<Map<String, dynamic>>().map(Producto.fromJson).toList();
  }

  // ---------- Cliente y cuentas ----------

  Future<Perfil> perfil(int clienteId) async {
    final json = await client.post('/clientes/buscar', {
      'id': clienteId,
      'incluirDomicilio': false,
      'incluirCuentas': true,
      'incluirUsuario': false,
    });
    final lista = (json is Map ? json['datos'] : null) as List? ?? [];
    if (lista.isEmpty) throw ApiException(404, 'No se encontró tu información de cliente.');
    return Perfil.fromJson(Map<String, dynamic>.from(lista.first as Map));
  }

  Future<void> establecerPin(String numeroCuenta, String pinNuevo, {String? pinActual}) =>
      client.post('/cuentas/pin', {
        'numeroCuenta': numeroCuenta,
        'pinNuevo': pinNuevo,
        if (pinActual != null && pinActual.isNotEmpty) 'pinActual': pinActual,
      });

  // ---------- Compras ----------

  /// Llave única por intento de compra: si la red falla y se reintenta con la misma, no se cobra dos veces.
  static String nuevaIdempotencyKey() {
    final r = Random.secure();
    return List.generate(32, (_) => r.nextInt(16).toRadixString(16)).join();
  }

  Future<ResultadoPago> pagar({
    required String numeroCuenta,
    required String pin,
    required double monto,
    required String telefonoReferencia,
    required Producto producto,
    required String idempotencyKey,
  }) async {
    final json = await client.post(
      '/pagos/servicio',
      {
        'numeroCuenta': numeroCuenta,
        'pin': pin,
        'monto': monto,
        'telefonoReferencia': telefonoReferencia,
        'idServicio': producto.idServicio,
        'idProducto': producto.idProducto,
        'tipoFront': producto.tipoFront,
      },
      {'Idempotency-Key': idempotencyKey},
    );
    return ResultadoPago.fromJson(_datos(json));
  }

  /// Estado actual de una compra (para resolver las que quedaron pendientes).
  Future<(EstadoCompra, String)> estadoCompra(int idTransaccion) async {
    final json = await client.post('/pagos/estado', {'idTransaccion': idTransaccion});
    final d = _datos(json);
    return (estadoCompraDe(d['estadoCompra'] as String?), '${d['mensaje'] ?? ''}');
  }

  Future<Pagina<Compra>> historial({int pagina = 0, int tamano = 20}) async {
    final json = await client.post('/pagos/historial', {'pagina': pagina, 'tamano': tamano});
    final d = _datos(json);
    final contenido = (d['contenido'] as List? ?? [])
        .whereType<Map<String, dynamic>>()
        .map(Compra.fromJson)
        .toList();
    return Pagina(contenido, (d['pagina'] as num?)?.toInt() ?? pagina,
        (d['totalPaginas'] as num?)?.toInt() ?? 1, (d['totalElementos'] as num?)?.toInt() ?? 0);
  }

  Future<Compra> detalleCompra(int idTransaccion) async {
    final json = await client.post('/pagos/historial/detalle', {'idTransaccion': idTransaccion});
    return Compra.fromJson(_datos(json));
  }
}
