/// Validaciones de los formularios (registro y edición de datos). Reflejan las reglas del backend, que es quien
/// valida en definitiva, pero con mensajes que dicen exactamente qué corregir mientras el usuario escribe.
library;

/// Tope de la calle: el nombre de calle real más largo que se conoce mide 72 caracteres; 80 deja margen.
const maxCalle = 80;
const minTexto = 3;

/// Claves de entidad federativa que admite la CURP (posiciones 12 y 13).
const _entidadesCurp = {
  'AS', 'BC', 'BS', 'CC', 'CL', 'CM', 'CS', 'CH', 'DF', 'DG', 'GT', 'GR', 'HG', 'JC', 'MC', 'MN', 'MS', 'NT',
  'NL', 'OC', 'PL', 'QT', 'QR', 'SP', 'SL', 'SR', 'TC', 'TS', 'TL', 'VZ', 'YN', 'ZS', 'NE',
};

final _letra = RegExp(r'\p{L}', unicode: true);

/// "aaa", "xxxx": un solo carácter repetido no es un dato real.
bool _todoIgual(String s) {
  final t = s.replaceAll(' ', '');
  return t.length >= 3 && RegExp(r'^(.)\1+$', caseSensitive: false).hasMatch(t);
}

String aammdd(DateTime d) =>
    '${(d.year % 100).toString().padLeft(2, '0')}${d.month.toString().padLeft(2, '0')}${d.day.toString().padLeft(2, '0')}';

// ------------------------------------------------------------------ correo

String? validarCorreo(String? v) {
  final s = v?.trim() ?? '';
  if (s.isEmpty) return 'Escribe tu correo';
  if (s.length > 100) return 'El correo no puede exceder 100 caracteres';
  final arroba = s.indexOf('@');
  if (arroba < 0) return 'Al correo le falta el @';
  if (arroba != s.lastIndexOf('@')) return 'El correo solo puede tener un @';
  final local = s.substring(0, arroba);
  final dominio = s.substring(arroba + 1);
  if (local.isEmpty) return 'Falta el nombre antes del @';
  if (local.length > 64 || !RegExp(r'^[A-Za-z0-9._%+\-]+$').hasMatch(local)) {
    return 'El nombre del correo tiene caracteres no válidos';
  }
  if (local.startsWith('.') || local.endsWith('.') || local.contains('..')) {
    return 'Antes del @ no puede haber puntos al inicio, al final ni dos seguidos';
  }
  if (dominio.isEmpty) return 'Falta el dominio después del @ (por ejemplo gmail.com)';
  final etiquetas = dominio.split('.');
  if (etiquetas.length < 2) return 'Falta la extensión del dominio (por ejemplo .com o .mx)';
  for (final e in etiquetas) {
    if (e.isEmpty) return 'El dominio no puede tener puntos seguidos ni terminar en punto';
    if (!RegExp(r'^[A-Za-z0-9]([A-Za-z0-9\-]{0,61}[A-Za-z0-9])?$').hasMatch(e)) {
      return 'El dominio del correo no es válido';
    }
  }
  if (!RegExp(r'^[A-Za-z]{2,24}$').hasMatch(etiquetas.last)) {
    return 'La extensión debe tener al menos 2 letras (por ejemplo .com o .mx)';
  }
  if (etiquetas.first.toLowerCase() == 'gmail' && dominio.toLowerCase() != 'gmail.com') {
    return '¿Quisiste escribir gmail.com?';
  }
  return null;
}

// ------------------------------------------------------------------ nombres

/// Nombre o apellido: solo letras, al menos 3 y como máximo 50.
String? validarNombrePropio(String s) {
  if (s.length > 50) return 'Máximo 50 caracteres';
  if (!RegExp(r"^[\p{L}\p{M}\s.'’\-]+$", unicode: true).hasMatch(s)) return 'Usa solo letras';
  if (_letra.allMatches(s).length < minTexto) return 'Escribe al menos $minTexto letras';
  if (_todoIgual(s)) return 'Escribe tu nombre real';
  return null;
}

/// Texto libre (ocupación, empresa, calle): mínimo 3 caracteres, algo de letras y sin repeticiones vacías.
String? validarTextoMinimo(String s, {String nombre = 'Este dato', int maximo = 100, bool requiereLetra = true}) {
  if (s.length < minTexto) return '$nombre debe tener al menos $minTexto caracteres';
  if (s.length > maximo) return '$nombre no puede exceder $maximo caracteres';
  if (requiereLetra && !_letra.hasMatch(s)) return '$nombre debe incluir letras';
  if (_todoIgual(s)) return 'Escribe un dato real para ${nombre.toLowerCase()}';
  return null;
}

String? validarCalle(String s) => validarTextoMinimo(s, nombre: 'La calle', maximo: maxCalle);

String? validarNumeroExterior(String s) {
  if (!RegExp(r'^[A-Za-z0-9\-/#. ]{1,20}$').hasMatch(s) || !RegExp(r'[A-Za-z0-9]').hasMatch(s)) {
    return 'Escribe el número exterior (por ejemplo 16 o 16-B)';
  }
  return null;
}

// ------------------------------------------------------------------ teléfono e importe

String? validarTelefono(String s) {
  if (!RegExp(r'^\d{10}$').hasMatch(s)) return s.length < 10 ? 'Faltan ${10 - s.length} dígitos' : 'Deben ser 10 dígitos';
  if (!RegExp(r'^[2-9]').hasMatch(s)) return 'Un teléfono de México no empieza con 0 ni con 1';
  if (RegExp(r'^(\d)\1{9}$').hasMatch(s)) return 'Escribe un teléfono real';
  return null;
}

String? validarIngreso(String s) {
  final n = double.tryParse(s);
  if (n == null || n <= 0) return 'Escribe un importe mayor a cero';
  if (n > 10000000) return 'El ingreso mensual no puede exceder \$10,000,000';
  return null;
}

// ------------------------------------------------------------------ CURP y RFC

/// H o M según el sexo elegido; null si no se ha elegido.
String? letraSexo(String? sexo) => switch (sexo) {
      'Masculino' => 'H',
      'Femenino' => 'M',
      _ => null,
    };

/// Formato, entidad, y coherencia con la fecha de nacimiento y el sexo ya capturados.
String? validarCurp(String s, {DateTime? nacimiento, String? sexo}) {
  final c = s.toUpperCase();
  if (c.length < 18) return 'La CURP tiene 18 caracteres (faltan ${18 - c.length})';
  if (!RegExp(r'^[A-Z]{4}\d{6}[HM][A-Z]{5}[A-Z0-9]\d$').hasMatch(c)) return 'La CURP no tiene un formato válido';
  if (!_entidadesCurp.contains(c.substring(11, 13))) return 'La CURP tiene una clave de entidad no válida';
  if (nacimiento != null && c.substring(4, 10) != aammdd(nacimiento)) {
    return 'La CURP no coincide con tu fecha de nacimiento';
  }
  final esperado = letraSexo(sexo);
  if (esperado != null && c[10] != esperado) return 'La CURP no coincide con el sexo seleccionado';
  return null;
}

/// RFC de persona física (13 caracteres, con homoclave); la fecha debe coincidir con la de nacimiento.
String? validarRfc(String s, {DateTime? nacimiento}) {
  final r = s.toUpperCase();
  if (r.length < 13) return 'El RFC de una persona física tiene 13 caracteres (faltan ${13 - r.length})';
  if (!RegExp(r'^[A-Z&Ñ]{4}\d{6}[A-Z0-9]{3}$').hasMatch(r)) return 'El RFC no tiene un formato válido';
  if (nacimiento != null && r.substring(4, 10) != aammdd(nacimiento)) {
    return 'El RFC no coincide con tu fecha de nacimiento';
  }
  return null;
}
