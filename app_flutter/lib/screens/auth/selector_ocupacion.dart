import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../api/gestopago_api.dart';
import '../../api/models.dart';

/// Lista de ocupaciones del catálogo (API) con buscador. Solo se puede elegir una ocupación de la lista.
class SelectorOcupacion extends StatefulWidget {
  const SelectorOcupacion({
    super.key,
    required this.api,
    required this.onChanged,
    this.inicial,
    this.errorText,
  });

  final GestoPagoApi api;
  final ValueChanged<String?> onChanged;

  /// Ocupación ya guardada; si no está en el catálogo, el usuario debe elegir una de la lista.
  final String? inicial;
  final String? errorText;

  @override
  State<SelectorOcupacion> createState() => _SelectorOcupacionState();
}

class _SelectorOcupacionState extends State<SelectorOcupacion> {
  late Future<List<Ocupacion>> _carga = widget.api.ocupaciones();
  final _controlador = TextEditingController();
  bool _inicializado = false;

  @override
  void dispose() {
    _controlador.dispose();
    super.dispose();
  }

  void _reintentar() => setState(() {
        _carga = widget.api.ocupaciones();
      });

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<Ocupacion>>(
      future: _carga,
      builder: (context, snap) {
        if (snap.connectionState != ConnectionState.done) {
          return const InputDecorator(
            decoration: InputDecoration(
                labelText: 'Ocupación', prefixIcon: Icon(Icons.work_outline), enabled: false),
            child: LinearProgressIndicator(),
          );
        }
        if (snap.hasError) {
          final e = snap.error;
          return InputDecorator(
            decoration: InputDecoration(
              labelText: 'Ocupación',
              prefixIcon: const Icon(Icons.work_outline),
              errorText: e is ApiException ? e.mensaje : 'No se pudo cargar el catálogo de ocupaciones',
              suffixIcon: IconButton(
                tooltip: 'Reintentar',
                icon: const Icon(Icons.refresh),
                onPressed: _reintentar,
              ),
            ),
            child: const SizedBox(height: 20),
          );
        }
        final lista = snap.data!;
        final t = Theme.of(context);
        final inicial = lista.any((o) => o.nombre == widget.inicial) ? widget.inicial : null;
        if (!_inicializado) {
          _controlador.text = inicial ?? '';
          _inicializado = true;
        }
        return DropdownMenu<String>(
          controller: _controlador,
          expandedInsets: EdgeInsets.zero,
          initialSelection: inicial,
          label: const Text('Ocupación'),
          leadingIcon: const Icon(Icons.work_outline),
          hintText: 'Escribe para buscar',
          helperText: 'Elige de la lista',
          errorText: widget.errorText,
          enableFilter: true,
          requestFocusOnTap: true,
          menuHeight: 320,
          dropdownMenuEntries: [
            for (final o in lista)
              DropdownMenuEntry<String>(
                value: o.nombre,
                label: o.nombre,
                labelWidget: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(o.nombre),
                    Text(o.grupo,
                        style: t.textTheme.bodySmall?.copyWith(color: t.colorScheme.onSurfaceVariant)),
                  ],
                ),
              ),
          ],
          onSelected: widget.onChanged,
        );
      },
    );
  }
}
