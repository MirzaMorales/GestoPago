import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../api/gestopago_api.dart';
import '../api/models.dart';
import '../state/session.dart';
import '../ui/filtros_entrada.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'auth/selector_domicilio.dart';

/// El cliente actualiza contacto, empleo y domicilio (PUT /clientes). Identidad y correo no se editan aquí.
class EditarDatosScreen extends StatefulWidget {
  const EditarDatosScreen({super.key, required this.session});

  final Session session;

  @override
  State<EditarDatosScreen> createState() => _EditarDatosScreenState();
}

class _EditarDatosScreenState extends State<EditarDatosScreen> {
  late Future<ClienteDetalle> _carga = _cargar();

  Future<ClienteDetalle> _cargar() => widget.session.api.clienteDetalle(widget.session.usuario!.clienteId!);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Editar mis datos')),
      body: FutureBuilder<ClienteDetalle>(
        future: _carga,
        builder: (context, snap) {
          if (snap.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError) return Mensaje.error(snap.error!, () => setState(() { _carga = _cargar(); }));
          return _Formulario(session: widget.session, cliente: snap.data!);
        },
      ),
    );
  }
}

class _Formulario extends StatefulWidget {
  const _Formulario({required this.session, required this.cliente});

  final Session session;
  final ClienteDetalle cliente;

  @override
  State<_Formulario> createState() => _FormularioState();
}

const _estadosCiviles = ['Soltero(a)', 'Casado(a)', 'Unión libre', 'Divorciado(a)', 'Viudo(a)'];

class _FormularioState extends State<_Formulario> {
  final _form = GlobalKey<FormState>();
  late final c = widget.cliente;
  late final _movil = TextEditingController(text: c.telefonoMovil);
  late final _alterno = TextEditingController(text: c.telefonoAlternativo);
  late final _ocupacion = TextEditingController(text: c.ocupacion);
  late final _empresa = TextEditingController(text: c.empresa);
  late final _ingreso = TextEditingController(
      text: c.ingresoMensual == null ? '' : c.ingresoMensual!.toStringAsFixed(2));
  late final _calle = TextEditingController(text: '${c.domicilio['calle'] ?? ''}');
  late final _exterior = TextEditingController(text: '${c.domicilio['numeroExterior'] ?? ''}');
  late final _interior = TextEditingController(text: '${c.domicilio['numeroInterior'] ?? ''}');
  late String? _estadoCivil = c.estadoCivil.isEmpty ? null : c.estadoCivil;
  late Colonia? _colonia = c.colonia;
  bool _guardando = false;
  String? _error;
  Map<String, String> _errServidor = {};

  @override
  void dispose() {
    for (final x in [_movil, _alterno, _ocupacion, _empresa, _ingreso, _calle, _exterior, _interior]) {
      x.dispose();
    }
    super.dispose();
  }

  Map<String, dynamic> _cuerpo() {
    String? opcional(String v) => v.trim().isEmpty ? null : v.trim();
    final j = c.json;
    return {
      'id': c.id,
      'nombre': j['nombre'],
      'segundoNombre': opcional('${j['segundoNombre'] ?? ''}'),
      'apellidoPaterno': j['apellidoPaterno'],
      'apellidoMaterno': j['apellidoMaterno'],
      'fechaNacimiento': j['fechaNacimiento'],
      'sexo': j['sexo'],
      'nacionalidad': j['nacionalidad'] ?? 'Mexicana',
      'estadoCivil': _estadoCivil,
      'correo': j['correo'],
      'telefonoMovil': _movil.text.trim(),
      'telefonoAlternativo': opcional(_alterno.text),
      'ocupacion': _ocupacion.text.trim(),
      'empresa': _empresa.text.trim(),
      'ingresoMensual': double.parse(_ingreso.text.trim()),
      'domicilio': {
        'calle': _calle.text.trim(),
        'numeroExterior': _exterior.text.trim(),
        'numeroInterior': opcional(_interior.text),
        'colonia': _colonia!.colonia,
        'municipio': _colonia!.municipio,
        'estado': _colonia!.estado,
        'codigoPostal': _colonia!.codigoPostal,
        'pais': 'México',
      },
    };
  }

  Future<void> _guardar() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _guardando = true;
      _error = null;
      _errServidor = {};
    });
    try {
      await widget.session.api.actualizarCliente(_cuerpo());
      if (!mounted) return;
      mostrarSnack(context, 'Tus datos se actualizaron correctamente.');
      Navigator.of(context).pop(true);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.mensaje;
        _errServidor = Map.of(e.detalles);
        if (e.mensaje.contains('código postal')) _errServidor['domicilio'] = e.mensaje;
      });
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  InputDecoration _deco(String campo, String etiqueta, {IconData? icono, bool opcional = false, String? prefijo}) =>
      InputDecoration(
        labelText: opcional ? '$etiqueta (opcional)' : etiqueta,
        prefixIcon: icono != null ? Icon(icono) : null,
        prefixText: prefijo,
        counterText: '',
        errorText: _errServidor[campo],
        errorMaxLines: 3,
      );

  String? _requerido(String? v) => (v ?? '').trim().isEmpty ? 'Este dato es obligatorio' : null;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final ancho = MediaQuery.sizeOf(context).width >= 700;
    Widget fila(List<Widget> hijos) => ancho
        ? Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            for (var i = 0; i < hijos.length; i++) ...[
              if (i > 0) const SizedBox(width: 12),
              Expanded(child: hijos[i]),
            ]
          ])
        : Column(children: hijos);
    const separador = SizedBox(height: 14);

    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 32),
      child: Centrado(
        maxWidth: 760,
        child: Form(
          key: _form,
          autovalidateMode: AutovalidateMode.onUserInteraction,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(children: [
                        Icon(Icons.badge_outlined, color: t.colorScheme.primary),
                        const SizedBox(width: 8),
                        Text('Datos de identidad', style: t.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
                      ]),
                      const SizedBox(height: 4),
                      Text('Para corregirlos comunícate con soporte.',
                          style: t.textTheme.bodySmall?.copyWith(color: t.colorScheme.onSurfaceVariant)),
                      const Divider(height: 24),
                      FilaDato('Nombre', c.nombreCompleto),
                      FilaDato('Fecha de nacimiento', fechaLarga(c.fechaNacimiento)),
                      FilaDato('CURP', c.curp),
                      FilaDato('RFC', c.rfc),
                      FilaDato('Correo', c.correo),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 20),
              Text('Contacto y empleo', style: t.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(height: 12),
              fila([
                TextFormField(
                  controller: _movil,
                  keyboardType: TextInputType.phone,
                  maxLength: 10,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                  decoration: _deco('telefonoMovil', 'Teléfono móvil', icono: Icons.smartphone),
                  validator: (v) => RegExp(r'^\d{10}$').hasMatch(v ?? '') ? null : 'Deben ser 10 dígitos',
                ),
                TextFormField(
                  controller: _alterno,
                  keyboardType: TextInputType.phone,
                  maxLength: 10,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                  decoration: _deco('telefonoAlternativo', 'Otro teléfono', opcional: true),
                  validator: (v) => (v ?? '').isEmpty || RegExp(r'^\d{10}$').hasMatch(v!) ? null : 'Deben ser 10 dígitos',
                ),
              ]),
              separador,
              DropdownButtonFormField<String>(
                initialValue: _estadoCivil,
                decoration: _deco('estadoCivil', 'Estado civil', icono: Icons.favorite_border),
                items: [
                  for (final e in {..._estadosCiviles, ?_estadoCivil})
                    DropdownMenuItem(value: e, child: Text(e)),
                ],
                onChanged: (v) => setState(() => _estadoCivil = v),
                validator: (v) => v == null ? 'Selecciona una opción' : null,
              ),
              separador,
              fila([
                TextFormField(
                  controller: _ocupacion,
                  maxLength: 100,
                  inputFormatters: [FiltrosEntrada.textoLibre],
                  decoration: _deco('ocupacion', 'Ocupación', icono: Icons.work_outline),
                  validator: _requerido,
                ),
                TextFormField(
                  controller: _empresa,
                  maxLength: 100,
                  inputFormatters: [FiltrosEntrada.textoLibre],
                  decoration: _deco('empresa', 'Empresa', icono: Icons.apartment_outlined),
                  validator: _requerido,
                ),
              ]),
              separador,
              TextFormField(
                controller: _ingreso,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'^\d{0,13}\.?\d{0,2}'))],
                decoration: _deco('ingresoMensual', 'Ingreso mensual', icono: Icons.payments_outlined, prefijo: r'$ '),
                validator: (v) {
                  final n = double.tryParse(v ?? '');
                  return n == null || n <= 0 ? 'Escribe un importe mayor a cero' : null;
                },
              ),
              const SizedBox(height: 24),
              Text('Domicilio', style: t.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(height: 12),
              FormField<Colonia>(
                initialValue: _colonia,
                validator: (_) => _colonia == null ? 'Elige tu colonia del catálogo' : null,
                builder: (campo) => SelectorDomicilio(
                  api: widget.session.api,
                  inicial: _colonia,
                  errorText: campo.errorText ?? _errServidor['domicilio'],
                  onChanged: (col) {
                    setState(() {
                      _colonia = col;
                      _errServidor.remove('domicilio');
                    });
                    campo.didChange(col);
                  },
                ),
              ),
              separador,
              TextFormField(
                controller: _calle,
                maxLength: 100,
                inputFormatters: [FiltrosEntrada.textoLibre],
                decoration: _deco('domicilio.calle', 'Calle', icono: Icons.signpost_outlined),
                validator: _requerido,
              ),
              separador,
              fila([
                TextFormField(
                  controller: _exterior,
                  maxLength: 20,
                  inputFormatters: [FiltrosEntrada.textoLibre],
                  decoration: _deco('domicilio.numeroExterior', 'Número exterior'),
                  validator: _requerido,
                ),
                TextFormField(
                  controller: _interior,
                  maxLength: 20,
                  inputFormatters: [FiltrosEntrada.textoLibre],
                  decoration: _deco('domicilio.numeroInterior', 'Número interior', opcional: true),
                ),
              ]),
              if (_error != null) ...[
                const SizedBox(height: 16),
                Text(_error!, style: TextStyle(color: t.colorScheme.error)),
              ],
              const SizedBox(height: 24),
              FilledButton.icon(
                onPressed: _guardando ? null : _guardar,
                icon: _guardando
                    ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                    : const Icon(Icons.save_outlined),
                label: const Text('Guardar cambios'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
