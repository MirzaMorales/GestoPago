int? _int(dynamic v) => v is num ? v.toInt() : int.tryParse('${v ?? ''}');

double? _num(dynamic v) => v is num ? v.toDouble() : double.tryParse('${v ?? ''}');

String _str(dynamic v) => v == null ? '' : '$v';

DateTime? _fecha(dynamic v) => v == null ? null : DateTime.tryParse('$v')?.toLocal();

/// Usuario autenticado (respuesta de /auth/verificar-2fa).
class Usuario {
  Usuario({
    required this.token,
    required this.correo,
    required this.usuarioId,
    required this.clienteId,
    required this.rol,
  });

  final String token;
  final String correo;
  final int usuarioId;
  final int? clienteId;
  final String rol;

  bool get esCliente => rol == 'CLIENTE';

  factory Usuario.fromJson(Map<String, dynamic> j) => Usuario(
        token: _str(j['accessToken']),
        correo: _str(j['correo']),
        usuarioId: _int(j['usuarioId']) ?? 0,
        clienteId: _int(j['clienteId']),
        rol: _str(j['rol']),
      );

  Map<String, dynamic> toJson() => {
        'accessToken': token,
        'correo': correo,
        'usuarioId': usuarioId,
        'clienteId': clienteId,
        'rol': rol,
      };
}

/// Respuesta del paso 1 del login: falta el código enviado por correo.
class Desafio2fa {
  Desafio2fa(this.desafioId, this.correoEnmascarado, this.expiraEnSegundos);

  final String desafioId;
  final String correoEnmascarado;
  final int expiraEnSegundos;

  factory Desafio2fa.fromJson(Map<String, dynamic> j) => Desafio2fa(
        _str(j['desafioId']),
        _str(j['correoEnmascarado']),
        _int(j['expiraEnSegundos']) ?? 300,
      );
}

class Producto {
  Producto({
    required this.idProducto,
    required this.producto,
    required this.idServicio,
    required this.servicio,
    required this.idCatTipoServicio,
    required this.tipoFront,
    required this.precio,
    required this.legend,
    required this.categoria,
    required this.logoUrl,
    required this.disponibleParaCompra,
    required this.montoLibre,
  });

  final int idProducto;
  final String producto;
  final int idServicio;
  final String servicio;
  final int? idCatTipoServicio;
  final int? tipoFront;
  final double? precio;
  final String legend;
  final String categoria;
  final String? logoUrl;
  final bool disponibleParaCompra;
  final bool montoLibre;

  /// Tarjetas de regalo / códigos: no piden teléfono ni referencia.
  bool get esCanjeable => idCatTipoServicio == 10 || idCatTipoServicio == 11;

  /// Recargas con precio fijo: el teléfono debe tener 10 dígitos.
  bool get pideTelefono => !montoLibre && !esCanjeable;

  factory Producto.fromJson(Map<String, dynamic> j) {
    final tipoFront = _int(j['tipoFront']);
    final precio = _num(j['precio']);
    return Producto(
      idProducto: _int(j['idProducto']) ?? 0,
      producto: _str(j['producto']).trim(),
      idServicio: _int(j['idServicio']) ?? 0,
      servicio: _str(j['servicio']).trim(),
      idCatTipoServicio: _int(j['idCatTipoServicio']),
      tipoFront: tipoFront,
      precio: precio,
      legend: _str(j['legend']).trim(),
      categoria: j['categoria'] != null ? _str(j['categoria']) : 'Otros',
      logoUrl: j['logoUrl'] as String?,
      disponibleParaCompra: j['disponibleParaCompra'] is bool
          ? j['disponibleParaCompra'] as bool
          : (tipoFront == 2 || (tipoFront == 1 && (precio ?? 0) > 0)),
      montoLibre: j['montoLibre'] is bool ? j['montoLibre'] as bool : tipoFront == 2,
    );
  }
}

/// Asentamiento del catálogo SEPOMEX (colonia con su CP, municipio y estado).
class Colonia {
  const Colonia(this.codigoPostal, this.colonia, this.tipo, this.municipio, this.estado);

  final String codigoPostal;
  final String colonia;
  final String tipo;
  final String municipio;
  final String estado;

  String get etiqueta => tipo.isEmpty || tipo == 'Colonia' ? colonia : '$colonia ($tipo)';

  factory Colonia.fromJson(Map<String, dynamic> j) => Colonia(
        _str(j['codigoPostal']),
        _str(j['colonia']),
        _str(j['tipoAsentamiento']),
        _str(j['municipio']),
        _str(j['estado']),
      );

  @override
  bool operator ==(Object other) =>
      other is Colonia &&
      other.codigoPostal == codigoPostal &&
      other.colonia == colonia &&
      other.municipio == municipio &&
      other.estado == estado;

  @override
  int get hashCode => Object.hash(codigoPostal, colonia, municipio, estado);
}

class Cuenta {
  Cuenta(this.numeroCuenta, this.saldo, this.estatus);

  final String numeroCuenta;
  final double saldo;
  final String estatus;

  bool get activa => estatus == 'ACTIVA';

  String get enmascarada => numeroCuenta.length >= 4
      ? '•••• ${numeroCuenta.substring(numeroCuenta.length - 4)}'
      : numeroCuenta;

  factory Cuenta.fromJson(Map<String, dynamic> j) =>
      Cuenta(_str(j['numeroCuenta']), _num(j['saldo']) ?? 0, _str(j['estatus']));
}

class Perfil {
  Perfil(this.nombreCompleto, this.correo, this.telefono, this.cuentas);

  final String nombreCompleto;
  final String correo;
  final String telefono;
  final List<Cuenta> cuentas;

  factory Perfil.fromJson(Map<String, dynamic> j) {
    final nombre = [j['nombre'], j['segundoNombre'], j['apellidoPaterno'], j['apellidoMaterno']]
        .where((p) => p != null && '$p'.trim().isNotEmpty)
        .join(' ');
    final cuentas = (j['cuentas'] as List? ?? [])
        .whereType<Map<String, dynamic>>()
        .map(Cuenta.fromJson)
        .toList();
    return Perfil(nombre, _str(j['correo']), _str(j['telefonoMovil']), cuentas);
  }
}

enum EstadoCompra { exitosa, rechazada, pendiente, errorComunicacion }

EstadoCompra estadoCompraDe(String? v) => switch (v) {
      'EXITOSA' => EstadoCompra.exitosa,
      'PENDIENTE' => EstadoCompra.pendiente,
      'ERROR_COMUNICACION' => EstadoCompra.errorComunicacion,
      _ => EstadoCompra.rechazada,
    };

/// Respuesta de una compra exitosa (POST /pagos/servicio).
class ResultadoPago {
  ResultadoPago({
    required this.idTransaccion,
    required this.monto,
    required this.saldoRestante,
    required this.producto,
    required this.referencia,
    required this.comision,
    required this.idTxGestoPago,
    required this.numAutorizacion,
    required this.fecha,
    required this.pinProducto,
    required this.leyenda,
  });

  final int? idTransaccion;
  final double? monto;
  final double? saldoRestante;
  final String producto;
  final String referencia;
  final double? comision;
  final String idTxGestoPago;
  final String numAutorizacion;
  final String fecha;
  final String pinProducto;
  final String leyenda;

  factory ResultadoPago.fromJson(Map<String, dynamic> j) => ResultadoPago(
        idTransaccion: _int(j['idTransaccion']),
        monto: _num(j['monto']),
        saldoRestante: _num(j['saldoRestante']),
        producto: _str(j['producto']),
        referencia: _str(j['referencia']),
        comision: _num(j['comision']),
        idTxGestoPago: _str(j['idTxGestoPago']),
        numAutorizacion: _str(j['numAutorizacion']),
        fecha: _str(j['fecha']),
        pinProducto: _str(j['pinProducto']),
        leyenda: _str(j['leyenda']),
      );
}

/// Compra exitosa del historial.
class Compra {
  Compra({
    required this.idTransaccion,
    required this.fecha,
    required this.idProducto,
    required this.producto,
    required this.referencia,
    required this.monto,
    required this.comision,
    required this.idTxGestoPago,
    required this.numAutorizacion,
    required this.cuenta,
    required this.estatus,
    required this.idServicio,
  });

  final int idTransaccion;
  final DateTime? fecha;
  final int? idProducto;
  final int? idServicio;
  final String producto;
  final String referencia;
  final double monto;
  final double? comision;
  final String idTxGestoPago;
  final String numAutorizacion;
  final String cuenta;
  final String estatus;

  factory Compra.fromJson(Map<String, dynamic> j) => Compra(
        idTransaccion: _int(j['idTransaccion']) ?? 0,
        fecha: _fecha(j['fecha']),
        idProducto: _int(j['idProducto']),
        idServicio: _int(j['idServicio']),
        producto: _str(j['producto']),
        referencia: _str(j['referencia']),
        monto: _num(j['monto']) ?? 0,
        comision: _num(j['comision']),
        idTxGestoPago: _str(j['idTxGestoPago']),
        numAutorizacion: _str(j['numAutorizacion']),
        cuenta: _str(j['cuenta']),
        estatus: _str(j['estatus']),
      );
}

class Pagina<T> {
  Pagina(this.contenido, this.pagina, this.totalPaginas, this.totalElementos);

  final List<T> contenido;
  final int pagina;
  final int totalPaginas;
  final int totalElementos;

  bool get hayMas => pagina + 1 < totalPaginas;
}
