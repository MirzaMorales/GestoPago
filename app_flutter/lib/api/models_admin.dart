import 'models.dart';

String _s(dynamic v) => v == null ? '' : '$v';

int? _i(dynamic v) => v is num ? v.toInt() : int.tryParse('${v ?? ''}');

double? _d(dynamic v) => v is num ? v.toDouble() : double.tryParse('${v ?? ''}');

/// Expediente completo de un cliente (POST /clientes/buscar con domicilio, cuentas y usuario).
class ClienteDetalle {
  ClienteDetalle(this.json);

  /// Se conserva el JSON original para reenviar sin pérdida los campos que no se editan (PUT /clientes).
  final Map<String, dynamic> json;

  int get id => _i(json['id']) ?? 0;
  String get nombre => _s(json['nombre']);
  String get segundoNombre => _s(json['segundoNombre']);
  String get apellidoPaterno => _s(json['apellidoPaterno']);
  String get apellidoMaterno => _s(json['apellidoMaterno']);
  String get nombreCompleto => [nombre, segundoNombre, apellidoPaterno, apellidoMaterno]
      .where((p) => p.trim().isNotEmpty)
      .join(' ');
  DateTime? get fechaNacimiento => DateTime.tryParse(_s(json['fechaNacimiento']));
  String get curp => _s(json['curp']);
  String get rfc => _s(json['rfc']);
  String get sexo => _s(json['sexo']);
  String get nacionalidad => _s(json['nacionalidad']);
  String get estadoCivil => _s(json['estadoCivil']);
  String get correo => _s(json['correo']);
  String get telefonoMovil => _s(json['telefonoMovil']);
  String get telefonoAlternativo => _s(json['telefonoAlternativo']);
  String get ocupacion => _s(json['ocupacion']);
  String get empresa => _s(json['empresa']);
  double? get ingresoMensual => _d(json['ingresoMensual']);
  bool get activo => json['activo'] == true;
  DateTime? get fechaCreacion => DateTime.tryParse(_s(json['fechaCreacion']))?.toLocal();

  Map<String, dynamic> get domicilio =>
      json['domicilio'] is Map ? Map<String, dynamic>.from(json['domicilio'] as Map) : {};

  /// Colonia del domicilio guardado, para precargar el selector del catálogo.
  Colonia? get colonia {
    final d = domicilio;
    if (_s(d['codigoPostal']).isEmpty) return null;
    return Colonia(_s(d['codigoPostal']), _s(d['colonia']), '', _s(d['municipio']), _s(d['estado']));
  }

  String get domicilioTexto {
    final d = domicilio;
    if (d.isEmpty) return '';
    final interior = _s(d['numeroInterior']).isEmpty ? '' : ' int. ${_s(d['numeroInterior'])}';
    return '${_s(d['calle'])} ${_s(d['numeroExterior'])}$interior, ${_s(d['colonia'])}, '
        'CP ${_s(d['codigoPostal'])}, ${_s(d['municipio'])}, ${_s(d['estado'])}';
  }

  List<Cuenta> get cuentas => (json['cuentas'] as List? ?? [])
      .whereType<Map<String, dynamic>>()
      .map(Cuenta.fromJson)
      .toList();

  int? get usuarioId => json['usuario'] is Map ? _i((json['usuario'] as Map)['id']) : null;
}

/// Compra con resultado desconocido que espera conciliación (POST /pagos/conciliacion/pendientes).
class CompraPendiente {
  CompraPendiente(Map<String, dynamic> j)
      : id = _i(j['id']) ?? 0,
        numeroCuenta = _s(j['numeroCuenta']),
        idProducto = _i(j['idProducto']),
        idServicio = _i(j['idServicio']),
        monto = _d(j['monto']) ?? 0,
        estatus = _s(j['estatus']),
        upc = _s(j['upc']),
        fechaEnvio = DateTime.tryParse(_s(j['fechaEnvio']))?.toLocal(),
        detalle = _s(j['detalle']),
        requiereResolucionManual = j['requiereResolucionManual'] == true;

  final int id;
  final String numeroCuenta;
  final int? idProducto;
  final int? idServicio;
  final double monto;
  final String estatus;
  final String upc;
  final DateTime? fechaEnvio;
  final String detalle;
  final bool requiereResolucionManual;

  String get cuentaEnmascarada => numeroCuenta.length >= 4
      ? '•••• ${numeroCuenta.substring(numeroCuenta.length - 4)}'
      : numeroCuenta;
}
