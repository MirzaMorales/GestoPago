import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/ui/validadores.dart';

void main() {
  group('correo', () {
    test('rechaza el caso del profesor: mmorales@gmail.c', () {
      expect(validarCorreo('mmorales@gmail.c'), 'La extensión debe tener al menos 2 letras (por ejemplo .com o .mx)');
    });

    test('rechaza formatos incompletos o mal escritos', () {
      expect(validarCorreo(''), 'Escribe tu correo');
      expect(validarCorreo('mmorales'), 'Al correo le falta el @');
      expect(validarCorreo('a@b@c.com'), 'El correo solo puede tener un @');
      expect(validarCorreo('@gmail.com'), 'Falta el nombre antes del @');
      expect(validarCorreo('a@'), contains('Falta el dominio'));
      expect(validarCorreo('a@gmail'), contains('Falta la extensión'));
      expect(validarCorreo('a..b@gmail.com'), contains('puntos'));
      expect(validarCorreo('.a@gmail.com'), contains('puntos'));
      expect(validarCorreo('a@gmail..com'), contains('puntos seguidos'));
      expect(validarCorreo('a@-gmail.com'), 'El dominio del correo no es válido');
      expect(validarCorreo('a@gmail.c0m'), contains('extensión'));
      expect(validarCorreo('a b@gmail.com'), contains('caracteres no válidos'));
    });

    test('sugiere gmail.com cuando se escribe mal', () {
      expect(validarCorreo('a@gmail.co'), '¿Quisiste escribir gmail.com?');
      expect(validarCorreo('a@gmail.con'), '¿Quisiste escribir gmail.com?');
    });

    test('acepta correos comunes', () {
      for (final c in ['mmorales@gmail.com', 'ana.lopez+gp@correo.unam.mx', 'a_b-c@sub.dominio.com.mx']) {
        expect(validarCorreo(c), isNull, reason: c);
      }
    });
  });

  group('nombres y textos', () {
    test('mínimo 3 letras y solo letras', () {
      expect(validarNombrePropio('Al'), 'Escribe al menos 3 letras');
      expect(validarNombrePropio('Ana'), isNull);
      expect(validarNombrePropio('María José'), isNull);
      expect(validarNombrePropio('Ana3'), 'Usa solo letras');
      expect(validarNombrePropio('...'), 'Escribe al menos 3 letras');
      expect(validarNombrePropio('aaaa'), 'Escribe tu nombre real');
    });

    test('textos libres: mínimo 3, con letras y sin repetir un carácter', () {
      expect(validarTextoMinimo('XY', nombre: 'La empresa'), 'La empresa debe tener al menos 3 caracteres');
      expect(validarTextoMinimo('123', nombre: 'La empresa'), 'La empresa debe incluir letras');
      expect(validarTextoMinimo('xxxx', nombre: 'La empresa'), 'Escribe un dato real para la empresa');
      expect(validarTextoMinimo('Pérez & Hijos', nombre: 'La empresa'), isNull);
    });

    test('la calle admite la más larga del mundo (72 caracteres) y tiene tope de 80', () {
      const larga = 'Dwudziestego Pierwszego Praskiego Pułku Piechoty imienia Dzieci Warszawy';
      expect(larga.length, 72);
      expect(validarCalle(larga), isNull);
      expect(validarCalle('a' * 81), 'La calle no puede exceder 80 caracteres');
      expect(validarCalle('Av'), 'La calle debe tener al menos 3 caracteres');
    });

    test('número exterior', () {
      expect(validarNumeroExterior('16-B'), isNull);
      expect(validarNumeroExterior('---'), isNotNull);
    });
  });

  group('teléfono e ingreso', () {
    test('teléfono mexicano', () {
      expect(validarTelefono('5512345678'), isNull);
      expect(validarTelefono('55123'), 'Faltan 5 dígitos');
      expect(validarTelefono('0123456789'), 'Un teléfono de México no empieza con 0 ni con 1');
      expect(validarTelefono('5555555555'), 'Escribe un teléfono real');
    });

    test('ingreso mensual', () {
      expect(validarIngreso('0'), 'Escribe un importe mayor a cero');
      expect(validarIngreso('25000.50'), isNull);
      expect(validarIngreso('10000000.01'), contains('no puede exceder'));
    });
  });

  group('CURP y RFC contra fecha y sexo', () {
    final nacimiento = DateTime(1995, 5, 20);

    test('CURP coherente', () {
      expect(validarCurp('LORA950520MDFPZN01', nacimiento: nacimiento, sexo: 'Femenino'), isNull);
    });

    test('CURP incompleta, con formato inválido o entidad inexistente', () {
      expect(validarCurp('LORA95'), 'La CURP tiene 18 caracteres (faltan 12)');
      expect(validarCurp('LORA950520MDFPZN0X'), 'La CURP no tiene un formato válido');
      expect(validarCurp('LORA950520MXXPZN01'), 'La CURP tiene una clave de entidad no válida');
    });

    test('CURP que no coincide con la fecha de nacimiento ni con el sexo', () {
      expect(validarCurp('LORA960520MDFPZN01', nacimiento: nacimiento), 'La CURP no coincide con tu fecha de nacimiento');
      expect(validarCurp('LORA950520HDFPZN01', nacimiento: nacimiento, sexo: 'Femenino'),
          'La CURP no coincide con el sexo seleccionado');
    });

    test('RFC de persona física con la fecha de nacimiento', () {
      expect(validarRfc('LORA950520AB1', nacimiento: nacimiento), isNull);
      expect(validarRfc('LORA9505'), 'El RFC de una persona física tiene 13 caracteres (faltan 5)');
      expect(validarRfc('LORA950521AB1', nacimiento: nacimiento), 'El RFC no coincide con tu fecha de nacimiento');
    });
  });
}
