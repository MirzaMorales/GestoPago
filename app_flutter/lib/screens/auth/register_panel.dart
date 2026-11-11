import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';

import '../../api/api_client.dart';
import '../../state/session.dart';
import '../../ui/filtros_entrada.dart';
import '../../api/models.dart';
import '../../legal/documentos_legales.dart';
import '../../legal/legal_screen.dart';
import 'auth_common.dart';
import 'selector_domicilio.dart';

/// Alta de cliente en 4 pasos (POST /clientes). Las reglas reflejan las del backend,
/// que es quien valida en definitiva; sus errores se muestran junto al campo que corresponde.
class RegisterPanel extends StatefulWidget {
  const RegisterPanel({
    super.key,
    required this.session,
    required this.onIniciarSesion,
    required this.onRegistrado,
  });

  final Session session;
  final VoidCallback onIniciarSesion;
  final void Function(String correo, String? numeroCuenta) onRegistrado;

  @override
  State<RegisterPanel> createState() => _RegisterPanelState();
}

class _Paso {
  const _Paso(this.titulo, this.icono, this.campos);

  final String titulo;
  final IconData icono;

  /// Nombres de campo del backend que pertenecen a este paso (para ubicar errores del servidor).
  final List<String> campos;
}

const _pasos = [
  _Paso('Datos personales', Icons.badge_outlined,
      ['nombre', 'segundoNombre', 'apellidoPaterno', 'apellidoMaterno', 'fechaNacimiento', 'sexo',
       'estadoCivil', 'nacionalidad', 'curp', 'rfc']),
  _Paso('Contacto', Icons.contact_mail_outlined,
      ['correo', 'telefonoMovil', 'telefonoAlternativo', 'ocupacion', 'empresa', 'ingresoMensual']),
  _Paso('Domicilio', Icons.home_outlined,
      ['domicilio', 'domicilio.calle', 'domicilio.numeroExterior', 'domicilio.numeroInterior',
       'domicilio.colonia', 'domicilio.municipio', 'domicilio.estado', 'domicilio.codigoPostal',
       'domicilio.pais']),
  _Paso('Acceso', Icons.lock_outline, ['password', 'aceptaTerminos', 'versionTerminos']),
];

class _RegisterPanelState extends State<RegisterPanel> {
  int _paso = 0;
  final _forms = List.generate(_pasos.length, (_) => GlobalKey<FormState>());
  final _c = <String, TextEditingController>{};
  final _errServidor = <String, String>{};
  DateTime? _nacimiento;
  String? _sexo;
  String? _estadoCivil;

  /// Asentamiento elegido del catálogo SEPOMEX (colonia, municipio, estado y CP).
  Colonia? _colonia;
  bool _ocultar = true;
  bool _aceptaTerminos = false;
  bool _enviando = false;
  String? _error;

  TextEditingController _ctl(String campo, [String? inicial]) =>
      _c.putIfAbsent(campo, () => TextEditingController(text: inicial));

  String _v(String campo) => _c[campo]?.text.trim() ?? '';

  @override
  void dispose() {
    for (final c in _c.values) {
      c.dispose();
    }
    super.dispose();
  }

  // ---------- Navegación ----------

  void _siguiente() {
    if (!(_forms[_paso].currentState?.validate() ?? false)) return;
    if (_paso < _pasos.length - 1) {
      setState(() => _paso++);
    } else {
      _enviar();
    }
  }

  void _anterior() {
    if (_paso == 0) {
      widget.onIniciarSesion();
    } else {
      setState(() => _paso--);
    }
  }

  // ---------- Envío ----------

  Map<String, dynamic> _cuerpo() {
    String? opcional(String campo) => _v(campo).isEmpty ? null : _v(campo);
    return {
      'nombre': _v('nombre'),
      if (opcional('segundoNombre') != null) 'segundoNombre': _v('segundoNombre'),
      'apellidoPaterno': _v('apellidoPaterno'),
      'apellidoMaterno': _v('apellidoMaterno'),
      'fechaNacimiento': DateFormat('yyyy-MM-dd').format(_nacimiento!),
      'curp': _v('curp').toUpperCase(),
      'rfc': _v('rfc').toUpperCase(),
      'sexo': _sexo,
      'nacionalidad': 'Mexicana',
      'estadoCivil': _estadoCivil,
      'correo': _v('correo').toLowerCase(),
      'telefonoMovil': _v('telefonoMovil'),
      if (opcional('telefonoAlternativo') != null) 'telefonoAlternativo': _v('telefonoAlternativo'),
      'ocupacion': _v('ocupacion'),
      'empresa': _v('empresa'),
      'ingresoMensual': double.parse(_v('ingresoMensual')),
      'domicilio': {
        'calle': _v('domicilio.calle'),
        'numeroExterior': _v('domicilio.numeroExterior'),
        if (opcional('domicilio.numeroInterior') != null)
          'numeroInterior': _v('domicilio.numeroInterior'),
        'colonia': _colonia!.colonia,
        'municipio': _colonia!.municipio,
        'estado': _colonia!.estado,
        'codigoPostal': _colonia!.codigoPostal,
        'pais': 'México',
      },
      'password': _c['password']!.text,
      // Consentimiento expreso: el servidor guarda la versión aceptada y la fecha.
      'aceptaTerminos': _aceptaTerminos,
      'versionTerminos': versionLegal,
    };
  }

  Future<void> _enviar() async {
    setState(() {
      _enviando = true;
      _error = null;
      _errServidor.clear();
    });
    try {
      final cuenta = await widget.session.api.registrar(_cuerpo());
      if (mounted) widget.onRegistrado(_v('correo').toLowerCase(), cuenta);
    } on ApiException catch (e) {
      if (!mounted) return;
      _ubicarErrores(e);
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  /// Coloca cada error del servidor en su campo y regresa al primer paso que tenga uno.
  void _ubicarErrores(ApiException e) {
    final errores = <String, String>{};
    if (e.status == 400 && e.detalles.isNotEmpty) {
      errores.addAll(e.detalles);
    } else if (e.status == 409) {
      final m = e.mensaje.toLowerCase();
      if (m.contains('curp')) errores['curp'] = 'Esta CURP ya está registrada';
      if (m.contains('rfc')) errores['rfc'] = 'Este RFC ya está registrado';
      if (m.contains('correo')) errores['correo'] = 'Este correo ya tiene una cuenta';
    } else if (e.mensaje.contains('mayor de edad')) {
      errores['fechaNacimiento'] = e.mensaje;
    } else if (e.mensaje.contains('código postal')) {
      errores['domicilio'] = e.mensaje; // el servidor no reconoce la combinación CP / colonia
    } else if (e.mensaje.contains('Aviso de Privacidad')) {
      errores['aceptaTerminos'] = e.mensaje; // no aceptó o la versión ya no es la vigente
    }
    // La versión enviada ya no es la vigente: se muestra junto a la casilla y se pide aceptar de nuevo.
    if (errores.containsKey('versionTerminos')) {
      errores['aceptaTerminos'] = errores.remove('versionTerminos')!;
    }
    if (errores.containsKey('aceptaTerminos')) _aceptaTerminos = false;
    int? primero;
    for (var i = 0; i < _pasos.length && primero == null; i++) {
      if (errores.keys.any(_pasos[i].campos.contains)) primero = i;
    }
    setState(() {
      _errServidor.addAll(errores);
      if (primero != null) {
        _paso = primero;
        _error = 'Revisa los datos marcados.';
      } else {
        _error = e.mensaje;
      }
    });
  }

  // ---------- Campos ----------

  Widget _campo(
    String campo,
    String etiqueta, {
    IconData? icono,
    String? Function(String)? validar,
    bool opcional = false,
    TextInputType? teclado,
    List<TextInputFormatter>? formato,
    int? maxLength,
    String? ayuda,
    String? inicial,
    TextCapitalization mayusculas = TextCapitalization.words,
    Iterable<String>? autofill,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: TextFormField(
        controller: _ctl(campo, inicial),
        keyboardType: teclado,
        // Por defecto, solo texto seguro (sin HTML ni caracteres de control).
        inputFormatters: formato ?? [FiltrosEntrada.textoLibre],
        maxLength: maxLength,
        textCapitalization: mayusculas,
        autofillHints: autofill,
        textInputAction: TextInputAction.next,
        decoration: InputDecoration(
          labelText: opcional ? '$etiqueta (opcional)' : etiqueta,
          prefixIcon: icono != null ? Icon(icono) : null,
          helperText: ayuda,
          counterText: '',
          errorText: _errServidor[campo],
          errorMaxLines: 3,
        ),
        onChanged: (_) {
          if (_errServidor.containsKey(campo)) setState(() => _errServidor.remove(campo));
        },
        validator: (v) {
          final s = v?.trim() ?? '';
          if (s.isEmpty) return opcional ? null : 'Este dato es obligatorio';
          return validar?.call(s);
        },
      ),
    );
  }

  String? _soloLetras(String s) =>
      RegExp(r"^[\p{L}\p{M}\s.'’\-]{2,50}$", unicode: true).hasMatch(s)
          ? null
          : 'Usa solo letras (2 a 50)';

  Widget _seleccion(String campo, String etiqueta, IconData icono, List<String> opciones,
      String? valor, ValueChanged<String?> cambiar) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: DropdownButtonFormField<String>(
        initialValue: valor,
        isExpanded: true,
        decoration: InputDecoration(
          labelText: etiqueta,
          prefixIcon: Icon(icono),
          errorText: _errServidor[campo],
        ),
        items: [for (final o in opciones) DropdownMenuItem(value: o, child: Text(o))],
        onChanged: (v) {
          _errServidor.remove(campo);
          cambiar(v);
        },
        validator: (v) => v == null ? 'Selecciona una opción' : null,
      ),
    );
  }

  /// Dos columnas en pantallas anchas, una en teléfono.
  Widget _fila(List<Widget> hijos) => LayoutBuilder(
        builder: (context, c) => c.maxWidth < 440
            ? Column(children: hijos)
            : Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  for (var i = 0; i < hijos.length; i++) ...[
                    if (i > 0) const SizedBox(width: 12),
                    Expanded(child: hijos[i]),
                  ],
                ],
              ),
      );

  Future<void> _elegirFecha() async {
    final hoy = DateTime.now();
    final limite = DateTime(hoy.year - 18, hoy.month, hoy.day);
    final f = await showDatePicker(
      context: context,
      initialDate: _nacimiento ?? DateTime(limite.year - 7),
      firstDate: DateTime(1920),
      lastDate: limite,
      helpText: 'Fecha de nacimiento',
      initialEntryMode: DatePickerEntryMode.calendarOnly,
    );
    if (f != null) {
      setState(() {
        _nacimiento = f;
        _ctl('fechaNacimiento').text = DateFormat("d 'de' MMMM 'de' yyyy", 'es_MX').format(f);
        _errServidor.remove('fechaNacimiento');
      });
    }
  }

  List<Widget> _pasoPersonal() => [
        _fila([
          _campo('nombre', 'Nombre', formato: [FiltrosEntrada.soloLetras], maxLength: 50, icono: Icons.person_outline, validar: _soloLetras,
              autofill: const [AutofillHints.givenName]),
          _campo('segundoNombre', 'Segundo nombre', formato: [FiltrosEntrada.soloLetras], maxLength: 50, opcional: true, validar: _soloLetras),
        ]),
        _fila([
          _campo('apellidoPaterno', 'Apellido paterno', formato: [FiltrosEntrada.soloLetras], maxLength: 50, validar: _soloLetras,
              autofill: const [AutofillHints.familyName]),
          _campo('apellidoMaterno', 'Apellido materno', formato: [FiltrosEntrada.soloLetras], maxLength: 50, validar: _soloLetras),
        ]),
        Padding(
          padding: const EdgeInsets.only(bottom: 14),
          child: TextFormField(
            controller: _ctl('fechaNacimiento'),
            readOnly: true,
            onTap: _elegirFecha,
            decoration: InputDecoration(
              labelText: 'Fecha de nacimiento',
              prefixIcon: const Icon(Icons.cake_outlined),
              suffixIcon: const Icon(Icons.calendar_month_outlined),
              helperText: 'Debes ser mayor de edad',
              errorText: _errServidor['fechaNacimiento'],
            ),
            validator: (_) => _nacimiento == null ? 'Selecciona tu fecha de nacimiento' : null,
          ),
        ),
        _fila([
          _seleccion('sexo', 'Sexo', Icons.wc_outlined, const ['Masculino', 'Femenino'], _sexo,
              (v) => setState(() => _sexo = v)),
          _seleccion('estadoCivil', 'Estado civil', Icons.favorite_border,
              const ['Soltero(a)', 'Casado(a)', 'Unión libre', 'Divorciado(a)', 'Viudo(a)'],
              _estadoCivil, (v) => setState(() => _estadoCivil = v)),
        ]),
        // Por ahora el servicio es solo para personas de nacionalidad mexicana: el campo no se edita.
        Padding(
          padding: const EdgeInsets.only(bottom: 14),
          child: TextFormField(
            initialValue: 'Mexicana',
            readOnly: true,
            enabled: false,
            decoration: const InputDecoration(
              labelText: 'Nacionalidad',
              prefixIcon: Icon(Icons.flag_outlined),
              suffixIcon: Icon(Icons.lock_outline, size: 18),
              helperText: 'Por ahora el servicio es solo para personas de nacionalidad mexicana',
            ),
          ),
        ),
        _campo('curp', 'CURP',
            icono: Icons.fingerprint,
            maxLength: 18,
            mayusculas: TextCapitalization.characters,
            formato: [_Mayusculas(), FilteringTextInputFormatter.allow(RegExp(r'[A-Za-z0-9]'))],
            ayuda: '18 caracteres',
            validar: (s) => RegExp(r'^[A-Z]{4}\d{6}[HM][A-Z]{5}[A-Z0-9]\d$').hasMatch(s.toUpperCase())
                ? null
                : 'La CURP no tiene un formato válido'),
        _campo('rfc', 'RFC',
            icono: Icons.description_outlined,
            maxLength: 13,
            mayusculas: TextCapitalization.characters,
            formato: [_Mayusculas(), FilteringTextInputFormatter.allow(RegExp(r'[A-Za-z0-9&Ññ]'))],
            ayuda: 'Con homoclave, 12 o 13 caracteres',
            validar: (s) => RegExp(r'^[A-Z&Ñ]{3,4}\d{6}[A-Z0-9]{3}$').hasMatch(s.toUpperCase())
                ? null
                : 'El RFC no tiene un formato válido'),
      ];

  List<Widget> _pasoContacto() => [
        _campo('correo', 'Correo electrónico',
            formato: [FiltrosEntrada.correo],
            maxLength: 100,
            icono: Icons.mail_outline,
            teclado: TextInputType.emailAddress,
            mayusculas: TextCapitalization.none,
            autofill: const [AutofillHints.email],
            ayuda: 'Aquí recibirás tus códigos de verificación',
            validar: (s) => validarCorreo(s)),
        _fila([
          _campo('telefonoMovil', 'Teléfono móvil',
              icono: Icons.smartphone,
              teclado: TextInputType.phone,
              maxLength: 10,
              formato: [FilteringTextInputFormatter.digitsOnly],
              autofill: const [AutofillHints.telephoneNumber],
              validar: (s) => RegExp(r'^\d{10}$').hasMatch(s) ? null : 'Deben ser 10 dígitos'),
          _campo('telefonoAlternativo', 'Otro teléfono',
              opcional: true,
              teclado: TextInputType.phone,
              maxLength: 10,
              formato: [FilteringTextInputFormatter.digitsOnly],
              validar: (s) => RegExp(r'^\d{10}$').hasMatch(s) ? null : 'Deben ser 10 dígitos'),
        ]),
        _fila([
          _campo('ocupacion', 'Ocupación', icono: Icons.work_outline, maxLength: 100),
          _campo('empresa', 'Empresa', icono: Icons.apartment_outlined, maxLength: 100),
        ]),
        _campo('ingresoMensual', 'Ingreso mensual',
            icono: Icons.payments_outlined,
            teclado: const TextInputType.numberWithOptions(decimal: true),
            formato: [FilteringTextInputFormatter.allow(RegExp(r'^\d{0,13}\.?\d{0,2}'))],
            validar: (s) {
              final n = double.tryParse(s);
              return n == null || n <= 0 ? 'Escribe un importe mayor a cero' : null;
            }),
      ];

  /// Errores del servidor sobre colonia, municipio, estado o CP (los elige el catálogo, no se escriben).
  static const _camposUbicacion = [
    'domicilio',
    'domicilio.colonia',
    'domicilio.municipio',
    'domicilio.estado',
    'domicilio.codigoPostal',
    'domicilio.pais',
  ];

  String? get _errorUbicacion =>
      _camposUbicacion.map((k) => _errServidor[k]).whereType<String>().firstOrNull;

  List<Widget> _pasoDomicilio() => [
        FormField<Colonia>(
          initialValue: _colonia,
          validator: (_) => _colonia == null ? 'Elige tu colonia del catálogo' : null,
          builder: (campo) => SelectorDomicilio(
            api: widget.session.api,
            inicial: _colonia,
            errorText: campo.errorText ?? _errorUbicacion,
            onChanged: (c) {
              setState(() {
                _colonia = c;
                _camposUbicacion.forEach(_errServidor.remove);
              });
              campo.didChange(c);
            },
          ),
        ),
        const SizedBox(height: 14),
        _campo('domicilio.calle', 'Calle', icono: Icons.signpost_outlined, maxLength: 100,
            autofill: const [AutofillHints.streetAddressLine1]),
        _fila([
          _campo('domicilio.numeroExterior', 'Número exterior', maxLength: 20,
              mayusculas: TextCapitalization.characters),
          _campo('domicilio.numeroInterior', 'Número interior', opcional: true, maxLength: 20,
              mayusculas: TextCapitalization.characters),
        ]),
      ];

  static final _reglas = <(String, RegExp)>[
    ('Mínimo 8 caracteres', RegExp(r'.{8,}')),
    ('Una letra mayúscula', RegExp(r'[A-Z]')),
    ('Una letra minúscula', RegExp(r'[a-z]')),
    ('Un número', RegExp(r'\d')),
    ('Un carácter especial (!@#\$…)', RegExp(r'[^A-Za-z0-9\s]')),
  ];

  List<Widget> _pasoAcceso(ThemeData t) {
    final pass = _ctl('password').text;
    return [
      Padding(
        padding: const EdgeInsets.only(bottom: 14),
        child: TextFormField(
          controller: _ctl('password'),
          maxLength: FiltrosEntrada.maxPassword,
          inputFormatters: [FiltrosEntrada.sinControl],
          obscureText: _ocultar,
          autofillHints: const [AutofillHints.newPassword],
          decoration: InputDecoration(
            labelText: 'Contraseña',
            counterText: '',
            prefixIcon: const Icon(Icons.lock_outline),
            errorText: _errServidor['password'],
            errorMaxLines: 3,
            suffixIcon: IconButton(
              tooltip: _ocultar ? 'Mostrar contraseña' : 'Ocultar contraseña',
              icon: Icon(_ocultar ? Icons.visibility_outlined : Icons.visibility_off_outlined),
              onPressed: () => setState(() => _ocultar = !_ocultar),
            ),
          ),
          onChanged: (_) => setState(() => _errServidor.remove('password')),
          validator: (v) =>
              _reglas.every((r) => r.$2.hasMatch(v ?? '')) ? null : 'La contraseña no cumple las reglas',
        ),
      ),
      Padding(
        padding: const EdgeInsets.only(bottom: 14),
        child: TextFormField(
          controller: _ctl('confirmacion'),
          maxLength: FiltrosEntrada.maxPassword,
          inputFormatters: [FiltrosEntrada.sinControl],
          obscureText: _ocultar,
          decoration: const InputDecoration(
              labelText: 'Confirma la contraseña', prefixIcon: Icon(Icons.lock_reset_outlined), counterText: ''),
          validator: (v) => v == pass ? null : 'Las contraseñas no coinciden',
        ),
      ),
      Wrap(
        spacing: 16,
        runSpacing: 6,
        children: [
          for (final r in _reglas)
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  r.$2.hasMatch(pass) ? Icons.check_circle : Icons.radio_button_unchecked,
                  size: 18,
                  color: r.$2.hasMatch(pass) ? t.colorScheme.primary : t.colorScheme.outline,
                ),
                const SizedBox(width: 6),
                Text(r.$1, style: t.textTheme.bodySmall),
              ],
            ),
        ],
      ),
      const SizedBox(height: 20),
      Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: t.colorScheme.surfaceContainerHighest.withValues(alpha: 0.5),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: [
            Icon(Icons.account_balance_wallet_outlined, color: t.colorScheme.primary),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                'Al crear tu cuenta se abre una cuenta digital a tu nombre para pagar tus compras.',
                style: t.textTheme.bodySmall,
              ),
            ),
          ],
        ),
      ),
      const SizedBox(height: 12),
      FormField<bool>(
        initialValue: _aceptaTerminos,
        validator: (_) => _aceptaTerminos ? null : 'Para crear tu cuenta debes aceptar ambos documentos',
        builder: (campo) => Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Checkbox(
                  value: _aceptaTerminos,
                  onChanged: (v) {
                    setState(() {
                      _aceptaTerminos = v ?? false;
                      _errServidor.remove('aceptaTerminos');
                    });
                    campo.didChange(v);
                  },
                ),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Wrap(
                      crossAxisAlignment: WrapCrossAlignment.center,
                      children: [
                        Text('He leído y acepto los ', style: t.textTheme.bodyMedium),
                        _EnlaceLegal('Términos y Condiciones', terminosCondiciones),
                        Text(' y el ', style: t.textTheme.bodyMedium),
                        _EnlaceLegal('Aviso de Privacidad', avisoPrivacidad),
                        Text(', incluido el tratamiento de mis datos patrimoniales y financieros.',
                            style: t.textTheme.bodyMedium),
                      ],
                    ),
                  ),
                ),
              ],
            ),
            if ((campo.errorText ?? _errServidor['aceptaTerminos']) != null)
              Padding(
                padding: const EdgeInsets.only(left: 12, top: 2),
                child: Text(campo.errorText ?? _errServidor['aceptaTerminos']!,
                    style: TextStyle(color: t.colorScheme.error, fontSize: 12)),
              ),
          ],
        ),
      ),
    ];
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final ultimo = _paso == _pasos.length - 1;
    final contenido = switch (_paso) {
      0 => _pasoPersonal(),
      1 => _pasoContacto(),
      2 => _pasoDomicilio(),
      _ => _pasoAcceso(t),
    };

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const TituloAuth(
          titulo: 'Crea tu cuenta',
          subtitulo: 'Tus mismos datos te servirán en la web y en la app.',
        ),
        const SizedBox(height: 20),
        _Indicador(paso: _paso),
        const SizedBox(height: 20),
        Row(
          children: [
            Icon(_pasos[_paso].icono, color: t.colorScheme.primary),
            const SizedBox(width: 8),
            Expanded(
              child: Text('Paso ${_paso + 1} de ${_pasos.length} · ${_pasos[_paso].titulo}',
                  style: t.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
            ),
          ],
        ),
        const SizedBox(height: 16),
        AutofillGroup(
          child: Form(
            key: _forms[_paso],
            // Validación en tiempo real: el error aparece al escribir, no hasta enviar.
            autovalidateMode: AutovalidateMode.onUserInteraction,
            child: Column(children: contenido),
          ),
        ),
        if (_error != null) ...[
          const SizedBox(height: 4),
          AlertaAuth(_error!),
        ],
        const SizedBox(height: 20),
        Row(
          children: [
            Expanded(
              child: OutlinedButton(
                onPressed: _enviando ? null : _anterior,
                child: Text(_paso == 0 ? 'Ya tengo cuenta' : 'Atrás'),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: BotonPrincipal(
                texto: ultimo ? 'Crear cuenta' : 'Continuar',
                cargando: _enviando,
                onPressed: _siguiente,
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _Indicador extends StatelessWidget {
  const _Indicador({required this.paso});

  final int paso;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Semantics(
      label: 'Paso ${paso + 1} de ${_pasos.length}',
      child: Row(
        children: [
          for (var i = 0; i < _pasos.length; i++) ...[
            if (i > 0) const SizedBox(width: 6),
            Expanded(
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 250),
                height: 6,
                decoration: BoxDecoration(
                  color: i <= paso ? cs.primary : cs.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(3),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _Mayusculas extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(TextEditingValue oldValue, TextEditingValue newValue) =>
      newValue.copyWith(text: newValue.text.toUpperCase());
}

/// Texto subrayado que abre un documento legal sin perder lo capturado en el registro.
class _EnlaceLegal extends StatelessWidget {
  const _EnlaceLegal(this.texto, this.documento);

  final String texto;
  final DocumentoLegal documento;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Semantics(
      link: true,
      child: InkWell(
        onTap: () => LegalScreen.abrir(context, documento),
        child: Text(
          texto,
          style: t.textTheme.bodyMedium?.copyWith(
            color: t.colorScheme.primary,
            fontWeight: FontWeight.w600,
            decoration: TextDecoration.underline,
            decorationColor: t.colorScheme.primary,
          ),
        ),
      ),
    );
  }
}
