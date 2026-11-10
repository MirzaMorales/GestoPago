import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/api_client.dart';
import '../../api/gestopago_api.dart';
import '../../api/models.dart';

enum _Modo { codigoPostal, listas }

/// Elige un asentamiento real del catálogo SEPOMEX, por código postal o por estado → municipio → colonia.
/// Nada de esto se escribe a mano: el resultado es siempre una combinación válida (que el servidor vuelve a validar).
class SelectorDomicilio extends StatefulWidget {
  const SelectorDomicilio({
    super.key,
    required this.api,
    required this.onChanged,
    this.inicial,
    this.errorText,
  });

  final GestoPagoApi api;
  final ValueChanged<Colonia?> onChanged;
  final Colonia? inicial;
  final String? errorText;

  @override
  State<SelectorDomicilio> createState() => _SelectorDomicilioState();
}

class _SelectorDomicilioState extends State<SelectorDomicilio> {
  _Modo _modo = _Modo.codigoPostal;
  Colonia? _seleccion;

  // Modo código postal
  final _cp = TextEditingController();
  List<Colonia> _coloniasCp = const [];
  bool _buscandoCp = false;
  String? _errorCp;

  // Modo listas
  List<String> _estados = const [];
  List<String> _municipios = const [];
  List<Colonia> _coloniasMunicipio = const [];
  String? _estado;
  String? _municipio;
  bool _cargandoLista = false;
  String? _errorLista;

  @override
  void initState() {
    super.initState();
    final inicial = widget.inicial;
    if (inicial != null) {
      _seleccion = inicial;
      _cp.text = inicial.codigoPostal;
      _coloniasCp = [inicial];
      _buscarCp(conservar: inicial);
    }
  }

  @override
  void dispose() {
    _cp.dispose();
    super.dispose();
  }

  void _elegir(Colonia? c) {
    setState(() => _seleccion = c);
    widget.onChanged(c);
  }

  String _mensaje(Object e) => e is ApiException ? e.mensaje : 'No se pudo consultar el catálogo';

  // ---------- Código postal ----------

  Future<void> _buscarCp({Colonia? conservar}) async {
    final cp = _cp.text;
    if (cp.length != 5) return;
    setState(() {
      _buscandoCp = true;
      _errorCp = null;
    });
    try {
      final lista = await widget.api.coloniasPorCp(cp);
      if (!mounted || _cp.text != cp) return;
      setState(() {
        _coloniasCp = lista;
        if (lista.isEmpty) _errorCp = 'Este código postal no existe';
      });
      if (conservar != null && lista.contains(conservar)) {
        _elegir(conservar);
      } else {
        _elegir(lista.length == 1 ? lista.first : null);
      }
    } catch (e) {
      if (mounted) setState(() => _errorCp = _mensaje(e));
    } finally {
      if (mounted) setState(() => _buscandoCp = false);
    }
  }

  // ---------- Listas ----------

  Future<void> _cargar(Future<void> Function() accion) async {
    setState(() {
      _cargandoLista = true;
      _errorLista = null;
    });
    try {
      await accion();
    } catch (e) {
      if (mounted) setState(() => _errorLista = _mensaje(e));
    } finally {
      if (mounted) setState(() => _cargandoLista = false);
    }
  }

  Future<void> _cambiarAModoListas() async {
    setState(() => _modo = _Modo.listas);
    _elegir(null);
    if (_estados.isEmpty) {
      await _cargar(() async {
        final e = await widget.api.estados();
        if (mounted) setState(() => _estados = e);
      });
    }
  }

  void _cambiarAModoCp() {
    setState(() => _modo = _Modo.codigoPostal);
    _elegir(_coloniasCp.length == 1 ? _coloniasCp.first : null);
  }

  Future<void> _elegirEstado(String? estado) async {
    if (estado == null || estado == _estado) return;
    setState(() {
      _estado = estado;
      _municipio = null;
      _municipios = const [];
      _coloniasMunicipio = const [];
    });
    _elegir(null);
    await _cargar(() async {
      final m = await widget.api.municipios(estado);
      if (mounted && _estado == estado) setState(() => _municipios = m);
    });
  }

  Future<void> _elegirMunicipio(String? municipio) async {
    if (municipio == null || municipio == _municipio) return;
    setState(() {
      _municipio = municipio;
      _coloniasMunicipio = const [];
    });
    _elegir(null);
    await _cargar(() async {
      final c = await widget.api.colonias(_estado!, municipio);
      if (mounted && _municipio == municipio) setState(() => _coloniasMunicipio = c);
    });
  }

  // ---------- UI ----------

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const _SoloLectura(etiqueta: 'País', valor: 'México', icono: Icons.public),
        const SizedBox(height: 14),
        SegmentedButton<_Modo>(
          segments: const [
            ButtonSegment(
                value: _Modo.codigoPostal, icon: Icon(Icons.pin_drop_outlined), label: Text('Código postal')),
            ButtonSegment(value: _Modo.listas, icon: Icon(Icons.list_alt), label: Text('No lo sé')),
          ],
          selected: {_modo},
          showSelectedIcon: false,
          onSelectionChanged: (s) => s.first == _Modo.listas ? _cambiarAModoListas() : _cambiarAModoCp(),
        ),
        const SizedBox(height: 14),
        if (_modo == _Modo.codigoPostal) ..._porCodigoPostal(t) else ..._porListas(t),
        if (widget.errorText != null) ...[
          const SizedBox(height: 6),
          Text(widget.errorText!, style: TextStyle(color: t.colorScheme.error, fontSize: 12)),
        ],
      ],
    );
  }

  List<Widget> _porCodigoPostal(ThemeData t) => [
        TextField(
          controller: _cp,
          keyboardType: TextInputType.number,
          maxLength: 5,
          autofillHints: const [AutofillHints.postalCode],
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          decoration: InputDecoration(
            labelText: 'Código postal',
            counterText: '',
            prefixIcon: const Icon(Icons.markunread_mailbox_outlined),
            helperText: _errorCp == null ? 'Escribe los 5 dígitos y elige tu colonia' : null,
            errorText: _errorCp,
            suffixIcon: _buscandoCp
                ? const Padding(
                    padding: EdgeInsets.all(14),
                    child: SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)))
                : null,
          ),
          onChanged: (v) {
            if (v.length == 5) {
              _buscarCp();
            } else if (_coloniasCp.isNotEmpty || _seleccion != null || _errorCp != null) {
              setState(() {
                _coloniasCp = const [];
                _errorCp = null;
              });
              _elegir(null);
            }
          },
        ),
        if (_coloniasCp.isNotEmpty) ...[
          const SizedBox(height: 14),
          _Lista<Colonia>(
            key: ValueKey('colonias-cp-${_cp.text}'),
            etiqueta: 'Colonia',
            icono: Icons.holiday_village_outlined,
            opciones: _coloniasCp,
            texto: (c) => c.etiqueta,
            valor: _seleccion,
            onSelected: _elegir,
          ),
          const SizedBox(height: 14),
          _SoloLectura(
              etiqueta: 'Municipio o alcaldía',
              valor: _coloniasCp.first.municipio,
              icono: Icons.location_city_outlined),
          const SizedBox(height: 14),
          _SoloLectura(etiqueta: 'Estado', valor: _coloniasCp.first.estado, icono: Icons.map_outlined),
        ],
      ];

  List<Widget> _porListas(ThemeData t) => [
        if (_cargandoLista) const LinearProgressIndicator(),
        if (_errorLista != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Text(_errorLista!, style: TextStyle(color: t.colorScheme.error)),
          ),
        _Lista<String>(
          key: const ValueKey('estados'),
          etiqueta: 'Estado',
          icono: Icons.map_outlined,
          opciones: _estados,
          texto: (s) => s,
          valor: _estado,
          onSelected: _elegirEstado,
        ),
        const SizedBox(height: 14),
        _Lista<String>(
          key: ValueKey('municipios-$_estado'),
          etiqueta: 'Municipio o alcaldía',
          icono: Icons.location_city_outlined,
          opciones: _municipios,
          texto: (s) => s,
          valor: _municipio,
          habilitado: _estado != null && _municipios.isNotEmpty,
          onSelected: _elegirMunicipio,
        ),
        const SizedBox(height: 14),
        _Lista<Colonia>(
          key: ValueKey('colonias-$_estado-$_municipio'),
          etiqueta: 'Colonia',
          icono: Icons.holiday_village_outlined,
          opciones: _coloniasMunicipio,
          texto: (c) => '${c.etiqueta} · CP ${c.codigoPostal}',
          valor: _seleccion,
          habilitado: _municipio != null && _coloniasMunicipio.isNotEmpty,
          onSelected: _elegir,
        ),
        const SizedBox(height: 14),
        _SoloLectura(
          etiqueta: 'Código postal',
          valor: _seleccion?.codigoPostal ?? 'Se llena al elegir la colonia',
          icono: Icons.markunread_mailbox_outlined,
          vacio: _seleccion == null,
        ),
      ];
}

/// Lista desplegable con búsqueda (los municipios y colonias pueden ser cientos).
class _Lista<T> extends StatelessWidget {
  const _Lista({
    super.key,
    required this.etiqueta,
    required this.icono,
    required this.opciones,
    required this.texto,
    required this.valor,
    required this.onSelected,
    this.habilitado = true,
  });

  final String etiqueta;
  final IconData icono;
  final List<T> opciones;
  final String Function(T) texto;
  final T? valor;
  final ValueChanged<T?> onSelected;
  final bool habilitado;

  @override
  Widget build(BuildContext context) {
    return DropdownMenu<T>(
      expandedInsets: EdgeInsets.zero,
      enabled: habilitado && opciones.isNotEmpty,
      initialSelection: valor,
      label: Text(etiqueta),
      leadingIcon: Icon(icono),
      enableFilter: true,
      requestFocusOnTap: true,
      menuHeight: 320,
      hintText: opciones.length > 8 ? 'Escribe para buscar' : null,
      dropdownMenuEntries: [
        for (final o in opciones) DropdownMenuEntry<T>(value: o, label: texto(o)),
      ],
      onSelected: onSelected,
    );
  }
}

class _SoloLectura extends StatelessWidget {
  const _SoloLectura({required this.etiqueta, required this.valor, required this.icono, this.vacio = false});

  final String etiqueta;
  final String valor;
  final IconData icono;
  final bool vacio;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    return InputDecorator(
      decoration: InputDecoration(
        labelText: etiqueta,
        prefixIcon: Icon(icono),
        suffixIcon: Tooltip(
          message: 'Se completa con el catálogo de Correos de México',
          child: Icon(Icons.lock_outline, size: 18, color: t.colorScheme.outline),
        ),
        enabled: false,
      ),
      child: Text(
        valor,
        style: t.textTheme.bodyLarge?.copyWith(
            color: vacio ? t.colorScheme.onSurfaceVariant : t.colorScheme.onSurface),
      ),
    );
  }
}
