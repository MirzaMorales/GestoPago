/// Aviso de Privacidad y Términos y Condiciones de la app.
///
/// IMPORTANTE: el responsable del tratamiento es quien opera ESTA app (el equipo del proyecto),
/// no GestoPago, que es el proveedor externo de pagos. Completa los datos de [Responsable] antes de publicar.
library;

/// Debe coincidir con `app.legal.version` del backend: el registro envía esta versión como la aceptada.
const versionLegal = '2026-11-10';
const fechaLegal = '10 de noviembre de 2026';

/// Datos de quien responde por los datos personales. COMPLETAR antes de publicar.
class Responsable {
  static const nombre = 'Equipo de desarrollo de GestoPago App (proyecto académico)';
  static const domicilio = '[Completar: domicilio del responsable]';
  static const correo = '[Completar: correo de privacidad]';
}

class SeccionLegal {
  const SeccionLegal(this.titulo, this.parrafos, {this.puntos = const []});

  final String titulo;
  final List<String> parrafos;
  final List<String> puntos;
}

class DocumentoLegal {
  const DocumentoLegal({
    required this.titulo,
    required this.resumen,
    required this.secciones,
  });

  final String titulo;

  /// "En pocas palabras": lo esencial antes del texto completo.
  final List<String> resumen;
  final List<SeccionLegal> secciones;
}

const avisoPrivacidad = DocumentoLegal(
  titulo: 'Aviso de Privacidad',
  resumen: [
    'Pedimos solo los datos necesarios para abrir tu cuenta, verificar tu identidad y procesar tus compras.',
    'A GestoPago solo le enviamos lo indispensable de cada compra: producto, teléfono o referencia e importe.',
    'No vendemos tus datos ni los usamos para publicidad.',
    'Tu contraseña y tu PIN se guardan cifrados de forma irreversible; nadie puede verlos.',
    'Puedes acceder, corregir, cancelar u oponerte al uso de tus datos (derechos ARCO) cuando quieras.',
  ],
  secciones: [
    SeccionLegal('1. Responsable de tus datos', [
      '${Responsable.nombre}, con domicilio en ${Responsable.domicilio}, es responsable del tratamiento de tus '
          'datos personales conforme a la Ley Federal de Protección de Datos Personales en Posesión de los '
          'Particulares y demás normativa aplicable. Para cualquier asunto de privacidad escríbenos a '
          '${Responsable.correo}.',
      'GestoPago es un proveedor externo de servicios de pago con el que esta aplicación se conecta; '
          'no es el responsable de este aviso.',
    ]),
    SeccionLegal('2. Datos que recabamos', [
      'Para darte el servicio tratamos las siguientes categorías de datos:',
    ], puntos: [
      'Identificación: nombre, apellidos, fecha de nacimiento, sexo, estado civil, nacionalidad, CURP y RFC.',
      'Contacto: correo electrónico y teléfonos.',
      'Domicilio: calle, número, colonia, municipio, estado y código postal.',
      'Laborales y patrimoniales: ocupación, empresa e ingreso mensual.',
      'Financieros: número de tu cuenta digital, saldo y movimientos.',
      'De tus compras: producto, teléfono o referencia pagada, importe, fecha, folio y número de autorización.',
      'De seguridad: plataforma en la que inicias sesión (web o móvil), intentos de acceso y códigos de verificación.',
    ]),
    SeccionLegal('3. Datos patrimoniales y financieros', [
      'Tu ingreso mensual, tu saldo y tus movimientos son datos patrimoniales y financieros. Al marcar la '
          'casilla de aceptación nos das tu consentimiento expreso para tratarlos con las finalidades de este aviso. '
          'No recabamos datos personales sensibles.',
    ]),
    SeccionLegal('4. Para qué usamos tus datos', [
      'Finalidades necesarias para el servicio:',
    ], puntos: [
      'Crear y administrar tu cuenta y tu cuenta digital.',
      'Verificar tu identidad y que seas mayor de edad.',
      'Darte acceso seguro con verificación en dos pasos y una sola sesión activa.',
      'Procesar tus compras y pagos a través de GestoPago y mostrarte el resultado.',
      'Mostrarte tu historial y comprobantes de compra.',
      'Dar seguimiento a compras pendientes y, si no se aplicaron, reintegrar el importe a tu saldo.',
      'Prevenir fraudes, accesos no autorizados y proteger la plataforma.',
      'Cumplir obligaciones legales y atender requerimientos de autoridades.',
    ]),
    SeccionLegal('5. Usos que NO hacemos', [
      'No usamos tus datos para publicidad, mercadotecnia ni perfiles comerciales, y no los vendemos ni '
          'rentamos. Si algún día quisiéramos hacerlo, te pediríamos tu consentimiento por separado.',
    ]),
    SeccionLegal('6. Con quién compartimos tus datos', [
      'Solo compartimos lo estrictamente necesario:',
    ], puntos: [
      'GestoPago (proveedor de pagos): producto, teléfono o referencia e importe de cada compra. '
          'No le enviamos tu nombre, CURP, RFC, domicilio ni contraseña.',
      'Proveedores de infraestructura que actúan por nuestra cuenta (alojamiento del servidor, base de datos, '
          'almacenamiento de sesiones y envío de correos), obligados a proteger la información y a usarla solo '
          'para prestarnos el servicio.',
      'Autoridades competentes, únicamente cuando la ley lo exija.',
    ]),
    SeccionLegal('7. Cómo protegemos tus datos', [
      'Aplicamos medidas de seguridad administrativas, técnicas y físicas, entre ellas:',
    ], puntos: [
      'Toda la comunicación viaja cifrada por HTTPS (TLS).',
      'Tu contraseña y tu PIN se guardan con BCrypt, un cifrado irreversible.',
      'Verificación en dos pasos con un código de un solo uso que se valida en el servidor.',
      'Una sola sesión activa por usuario, que expira automáticamente después de un tiempo.',
      'Bloqueo temporal tras varios intentos fallidos de acceso o de PIN.',
      'Las operaciones enviadas a GestoPago van cifradas con AES-256.',
      'En la app tu sesión se guarda en el almacenamiento seguro del dispositivo, y los números de cuenta '
          'se muestran enmascarados.',
      'Cada usuario solo puede consultar su propia información, y los mensajes de error no exponen datos internos.',
    ]),
    SeccionLegal('8. Cuánto tiempo conservamos tus datos', [
      'Conservamos tus datos mientras tu cuenta esté activa y, después, solo durante el tiempo que exijan las '
          'obligaciones legales aplicables a las operaciones realizadas. Al terminar ese plazo los bloqueamos y '
          'los eliminamos de forma segura.',
    ]),
    SeccionLegal('9. Tus derechos ARCO y la revocación del consentimiento', [
      'Puedes Acceder a tus datos, Rectificarlos, Cancelarlos u Oponerte a su uso, así como revocar tu '
          'consentimiento o limitar su uso. Envía tu solicitud a ${Responsable.correo} con:',
    ], puntos: [
      'Tu nombre y el correo de tu cuenta.',
      'Una identificación oficial que acredite tu identidad (o la de tu representante).',
      'La descripción clara del derecho que quieres ejercer y de los datos involucrados.',
      'Cualquier documento que ayude a localizar tus datos.',
    ]),
    SeccionLegal('', [
      'Te responderemos en los plazos que establece la ley. Toma en cuenta que, si revocas el consentimiento '
          'para las finalidades necesarias, ya no podremos darte el servicio. Algunos datos podrían conservarse '
          'mientras exista una obligación legal.',
    ]),
    SeccionLegal('10. Cookies y almacenamiento local', [
      'La versión web no usa cookies de rastreo ni herramientas de analítica o publicidad. Solo guarda en tu '
          'navegador la información mínima para mantener tu sesión abierta; se borra al cerrar sesión.',
    ]),
    SeccionLegal('11. Cambios a este aviso', [
      'Si actualizamos este aviso te lo mostraremos en la app y te pediremos aceptarlo de nuevo. La versión '
          'vigente siempre está disponible en la aplicación.',
    ]),
    SeccionLegal('12. Autoridad', [
      'Si consideras que tu derecho a la protección de datos personales ha sido vulnerado, puedes acudir a la '
          'autoridad competente en la materia.',
    ]),
  ],
);

const terminosCondiciones = DocumentoLegal(
  titulo: 'Términos y Condiciones',
  resumen: [
    'Debes ser mayor de edad y registrar datos verdaderos; cada persona puede tener una sola cuenta.',
    'Tu contraseña, tu PIN y los códigos de verificación son personales: no los compartas.',
    'Una compra solo cuenta como realizada cuando GestoPago la confirma.',
    'Revisa bien el teléfono o la referencia antes de pagar: una compra confirmada no se puede cancelar.',
    'Si una compra no se aplica, el importe regresa a tu saldo.',
  ],
  secciones: [
    SeccionLegal('1. Aceptación', [
      'Estos Términos regulan el uso de GestoPago App en su versión web y móvil. Al crear tu cuenta y marcar '
          'la casilla de aceptación confirmas que los leíste y que estás de acuerdo con ellos y con el Aviso de '
          'Privacidad.',
      'GestoPago App es un prototipo desarrollado como proyecto académico que se conecta a los servicios de '
          'GestoPago para recargas, tarjetas de regalo y pago de servicios. No es una institución financiera.',
    ]),
    SeccionLegal('2. Requisitos para usar la app', [], puntos: [
      'Ser mayor de 18 años.',
      'Proporcionar información verdadera, completa y actualizada.',
      'Tener una sola cuenta: la CURP, el RFC y el correo no pueden repetirse.',
    ]),
    SeccionLegal('3. Tu cuenta y su seguridad', [], puntos: [
      'Eres responsable de mantener en secreto tu contraseña, tu PIN de compras y los códigos que te enviamos.',
      'Por seguridad solo puede haber una sesión activa: si entras desde otro dispositivo se te pedirá cerrar '
          'la sesión anterior.',
      'Las sesiones expiran automáticamente; cierra sesión al terminar, sobre todo en equipos compartidos.',
      'Avísanos de inmediato si crees que alguien más accedió a tu cuenta.',
    ]),
    SeccionLegal('4. Cuenta digital y saldo', [
      'Al registrarte se abre a tu nombre una cuenta digital. Tus compras se pagan con su saldo y cada '
          'operación se autoriza con tu PIN de 4 dígitos.',
    ]),
    SeccionLegal('5. Compras', [], puntos: [
      'Antes de pagar verás un resumen con el producto, el teléfono o la referencia y el total.',
      'Una compra solo se considera realizada cuando GestoPago la confirma como exitosa.',
      'Si GestoPago rechaza la operación, no se hace ningún cargo.',
      'Si queda pendiente, no la repitas: le damos seguimiento y se confirma o, si no se aplicó, se reintegra el '
          'importe a tu saldo.',
      'Eres responsable de capturar correctamente el teléfono, la referencia o el número de contrato.',
      'Los códigos de tarjetas de regalo se muestran al confirmar la compra; guárdalos en un lugar seguro.',
    ]),
    SeccionLegal('6. Cancelaciones y aclaraciones', [
      'Una compra confirmada no se puede cancelar desde la app, porque el proveedor ya la aplicó. Para cualquier '
          'aclaración escríbenos a ${Responsable.correo} con el folio y el número de autorización de la compra.',
    ]),
    SeccionLegal('7. Uso permitido', [
      'Te comprometes a usar la app solo para fines lícitos y personales. Está prohibido:',
    ], puntos: [
      'Suplantar a otra persona o registrar datos falsos.',
      'Intentar acceder a cuentas o información de otros usuarios.',
      'Interferir con la seguridad o el funcionamiento de la plataforma, o automatizar operaciones.',
      'Usar la app para operaciones con recursos de procedencia ilícita.',
    ]),
    SeccionLegal('8. Disponibilidad del servicio', [
      'El servicio depende de GestoPago y de otros proveedores externos, por lo que podría no estar disponible '
          'en algunos momentos, por ejemplo durante mantenimientos o fallas de terceros. Haremos lo posible por '
          'restablecerlo pronto.',
    ]),
    SeccionLegal('9. Suspensión y baja', [
      'Podemos suspender o dar de baja una cuenta si se incumplen estos Términos o si detectamos actividad '
          'sospechosa. También puedes solicitar la baja de tu cuenta en cualquier momento.',
    ]),
    SeccionLegal('10. Responsabilidad', [
      'No somos responsables de los servicios que prestan las compañías cuyos productos se pagan (telefonía, '
          'luz, agua, etc.), ni de errores causados por datos mal capturados. Los logotipos y marcas mostrados '
          'pertenecen a sus respectivos titulares.',
    ]),
    SeccionLegal('11. Cambios a estos Términos', [
      'Si los modificamos te avisaremos en la app y te pediremos aceptarlos de nuevo para seguir usándola.',
    ]),
    SeccionLegal('12. Legislación aplicable', [
      'Estos Términos se rigen por las leyes de los Estados Unidos Mexicanos.',
    ]),
  ],
);
