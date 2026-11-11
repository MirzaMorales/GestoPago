import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../api/gestopago_api.dart';
import '../api/models.dart';
import '../state/session.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'purchase_result_screen.dart';

/// Detalle del producto y compra: datos → confirmación → PIN → resultado.
/// Devuelve true al cerrarse si la compra fue exitosa.
class ProductScreen extends StatefulWidget {
  const ProductScreen({super.key, required this.session, required this.producto});

  final Session session;
  final Producto producto;

  @override
  State<ProductScreen> createState() => _ProductScreenState();
}

class _ProductScreenState extends State<ProductScreen> {
  final _form = GlobalKey<FormState>();
  final _referencia = TextEditingController();
  final _monto = TextEditingController();

  Future<Perfil>? _perfil;
  Cuenta? _cuenta;
  bool _pagando = false;

  Producto get p => widget.producto;
  Usuario get u => widget.session.usuario!;

  @override
  void initState() {
    super.initState();
    if (u.esCliente && u.clienteId != null) _cargarPerfil();
  }

  void _cargarPerfil() {
    setState(() {
      _perfil = widget.session.api.perfil(u.clienteId!).then((perfil) {
        final activas = perfil.cuentas.where((c) => c.activa).toList();
        if (mounted && activas.isNotEmpty) setState(() => _cuenta ??= activas.first);
        return perfil;
      });
    });
  }

  @override
  void dispose() {
    _referencia.dispose();
    _monto.dispose();
    super.dispose();
  }

  double? get _importe => p.montoLibre ? double.tryParse(_monto.text.replaceAll(',', '')) : p.precio;

  String get _valorReferencia => p.esCanjeable ? '1111111111' : _referencia.text.trim();

  Future<void> _comprar() async {
    if (!_form.currentState!.validate()) return;
    if (_cuenta == null) {
      mostrarSnack(context, 'Selecciona la cuenta con la que vas a pagar', error: true);
      return;
    }
    final importe = _importe!;
    if (importe > _cuenta!.saldo) {
      mostrarSnack(context, 'El saldo de la cuenta no alcanza para esta compra', error: true);
      return;
    }

    // 1) Confirmación explícita con el resumen de la operación + PIN de la cuenta.
    final pin = await showDialog<String>(
      context: context,
      barrierDismissible: false,
      builder: (_) => _DialogoConfirmacion(
        producto: p,
        importe: importe,
        referencia: p.esCanjeable ? null : _valorReferencia,
        cuenta: _cuenta!,
      ),
    );
    if (pin == null || !mounted) return;

    // 2) Envío a GestoPago con una llave de idempotencia nueva para este intento.
    setState(() => _pagando = true);
    final llave = GestoPagoApi.nuevaIdempotencyKey();
    ResultadoCompra resultado;
    try {
      final r = await widget.session.api.pagar(
        numeroCuenta: _cuenta!.numeroCuenta,
        pin: pin,
        monto: importe,
        telefonoReferencia: _valorReferencia,
        producto: p,
        idempotencyKey: llave,
      );
      resultado = ResultadoCompra.exitosa(r);
    } on ApiException catch (e) {
      if (e.status == 401) return; // la sesión terminó: la app ya regresa al login
      resultado = ResultadoCompra.desdeError(e);
    } finally {
      if (mounted) setState(() => _pagando = false);
    }
    if (!mounted) return;

    // 3) Resultado claro: exitosa, rechazada, pendiente o error de comunicación.
    final exito = await Navigator.of(context).push<bool>(MaterialPageRoute(
      fullscreenDialog: true,
      builder: (_) => PurchaseResultScreen(
        session: widget.session,
        producto: p,
        importe: importe,
        resultado: resultado,
      ),
    ));
    if (!mounted) return;
    if (exito == true) {
      Navigator.of(context).pop(true);
    } else {
      _cargarPerfil(); // refresca el saldo
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final ancho = MediaQuery.sizeOf(context).width >= 900;
    final info = _Informacion(producto: p);
    final compra = _formularioCompra(t);

    return Scaffold(
      appBar: AppBar(title: Text(p.servicio)),
      body: AbsorbPointer(
        absorbing: _pagando,
        child: Stack(
          children: [
            SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 32),
              child: Centrado(
                maxWidth: 1000,
                child: ancho
                    ? Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Expanded(child: info),
                          const SizedBox(width: 20),
                          Expanded(child: compra),
                        ],
                      )
                    : Column(children: [info, const SizedBox(height: 16), compra]),
              ),
            ),
            if (_pagando)
              Container(
                color: Colors.black26,
                alignment: Alignment.center,
                child: Card(
                  child: Padding(
                    padding: const EdgeInsets.all(28),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const CircularProgressIndicator(),
                        const SizedBox(height: 20),
                        Text('Procesando tu compra con GestoPago…', style: t.textTheme.titleSmall),
                        const SizedBox(height: 6),
                        Text('No cierres esta pantalla.', style: t.textTheme.bodySmall),
                      ],
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _formularioCompra(ThemeData t) {
    if (!u.esCliente) {
      return const Card(
        child: Mensaje(
          icono: Icons.admin_panel_settings_outlined,
          titulo: 'Compras solo para clientes',
          texto: 'Inicia sesión con una cuenta de cliente para comprar.',
        ),
      );
    }
    if (!p.disponibleParaCompra) {
      return const Card(
        child: Mensaje(
          icono: Icons.block,
          titulo: 'Producto no disponible',
          texto: 'Este producto no se puede comprar desde la aplicación.',
        ),
      );
    }
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Form(
          key: _form,
          // Validación en tiempo real: el error aparece al escribir, no hasta enviar.
          autovalidateMode: AutovalidateMode.onUserInteraction,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('Comprar', style: t.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(height: 16),
              if (p.pideTelefono)
                TextFormField(
                  controller: _referencia,
                  keyboardType: TextInputType.phone,
                  maxLength: 10,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                  decoration: const InputDecoration(
                    labelText: 'Número de teléfono',
                    helperText: '10 dígitos',
                    prefixIcon: Icon(Icons.smartphone),
                  ),
                  validator: (v) =>
                      RegExp(r'^\d{10}$').hasMatch(v ?? '') ? null : 'Escribe un teléfono de 10 dígitos',
                ),
              if (p.montoLibre) ...[
                TextFormField(
                  controller: _referencia,
                  maxLength: 40,
                  inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[A-Za-z0-9]'))],
                  decoration: const InputDecoration(
                    labelText: 'Número de referencia o contrato',
                    helperText: 'Lo encuentras en tu recibo',
                    prefixIcon: Icon(Icons.confirmation_number_outlined),
                  ),
                  validator: (v) => (v ?? '').trim().isEmpty ? 'Escribe la referencia del recibo' : null,
                ),
                const SizedBox(height: 8),
                TextFormField(
                  controller: _monto,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'^\d*\.?\d{0,2}'))],
                  decoration: const InputDecoration(
                    labelText: 'Importe a pagar',
                    prefixText: r'$ ',
                    prefixIcon: Icon(Icons.attach_money),
                  ),
                  onChanged: (_) => setState(() {}),
                  validator: (v) {
                    final n = double.tryParse(v ?? '');
                    if (n == null || n <= 0) return 'Escribe un importe mayor a cero';
                    return null;
                  },
                ),
              ],
              if (p.esCanjeable)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Text('Recibirás el código de la tarjeta al confirmar la compra.',
                      style: t.textTheme.bodyMedium),
                ),
              const SizedBox(height: 12),
              _selectorCuenta(t),
              const SizedBox(height: 20),
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: t.colorScheme.primaryContainer.withValues(alpha: 0.5),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(
                  children: [
                    Text('Total', style: t.textTheme.titleMedium),
                    const Spacer(),
                    Text(dinero(_importe),
                        style: t.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              FilledButton.icon(
                onPressed: _pagando ? null : _comprar,
                icon: const Icon(Icons.shopping_bag_outlined),
                label: const Text('Revisar y comprar'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _selectorCuenta(ThemeData t) {
    return FutureBuilder<Perfil>(
      future: _perfil,
      builder: (context, snap) {
        if (snap.connectionState != ConnectionState.done) {
          return const LinearProgressIndicator();
        }
        if (snap.hasError) {
          return ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.error_outline),
            title: const Text('No se pudieron cargar tus cuentas'),
            trailing: TextButton(onPressed: _cargarPerfil, child: const Text('Reintentar')),
          );
        }
        final activas = snap.data!.cuentas.where((c) => c.activa).toList();
        if (activas.isEmpty) {
          return const ListTile(
            contentPadding: EdgeInsets.zero,
            leading: Icon(Icons.account_balance_wallet_outlined),
            title: Text('No tienes cuentas activas para pagar'),
          );
        }
        return DropdownButtonFormField<String>(
          initialValue: _cuenta?.numeroCuenta,
          decoration: const InputDecoration(
              labelText: 'Pagar con', prefixIcon: Icon(Icons.account_balance_wallet_outlined)),
          items: [
            for (final c in activas)
              DropdownMenuItem(
                value: c.numeroCuenta,
                child: Text('Cuenta ${c.enmascarada}  ·  Saldo ${dinero(c.saldo)}'),
              ),
          ],
          onChanged: (v) => setState(() => _cuenta = activas.firstWhere((c) => c.numeroCuenta == v)),
        );
      },
    );
  }
}

class _Informacion extends StatelessWidget {
  const _Informacion({required this.producto});

  final Producto producto;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final p = producto;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                LogoServicio(url: p.logoUrl, nombre: p.servicio, size: 72),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(p.producto,
                          style: t.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
                      const SizedBox(height: 4),
                      Text(p.servicio,
                          style: t.textTheme.bodyMedium
                              ?.copyWith(color: t.colorScheme.onSurfaceVariant)),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                Chip(avatar: const Icon(Icons.category_outlined, size: 18), label: Text(p.categoria)),
                Chip(
                  avatar: Icon(p.disponibleParaCompra ? Icons.check_circle : Icons.cancel, size: 18,
                      color: p.disponibleParaCompra ? Estados.exito : Estados.rechazo),
                  label: Text(p.disponibleParaCompra ? 'Disponible' : 'No disponible'),
                ),
              ],
            ),
            const Divider(height: 32),
            FilaDato('Precio', p.montoLibre ? 'Importe de tu recibo' : dinero(p.precio), destacado: true),
            FilaDato('Servicio', p.servicio),
            FilaDato('Clave de producto', '${p.idProducto}'),
            if (p.legend.isNotEmpty) ...[
              const Divider(height: 32),
              Text('Descripción', style: t.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(height: 8),
              Text(p.legend, style: t.textTheme.bodyMedium),
            ],
          ],
        ),
      ),
    );
  }
}

/// Resumen de la compra + PIN de 4 dígitos. Devuelve el PIN si el usuario confirma.
class _DialogoConfirmacion extends StatefulWidget {
  const _DialogoConfirmacion({
    required this.producto,
    required this.importe,
    required this.referencia,
    required this.cuenta,
  });

  final Producto producto;
  final double importe;
  final String? referencia;
  final Cuenta cuenta;

  @override
  State<_DialogoConfirmacion> createState() => _DialogoConfirmacionState();
}

class _DialogoConfirmacionState extends State<_DialogoConfirmacion> {
  final _pin = TextEditingController();
  bool _oculto = true;

  @override
  void dispose() {
    _pin.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context);
    final valido = RegExp(r'^\d{4}$').hasMatch(_pin.text);
    return AlertDialog(
      title: const Text('Confirma tu compra'),
      content: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 400),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              FilaDato('Producto', widget.producto.producto),
              FilaDato('Servicio', widget.producto.servicio),
              if (widget.referencia != null)
                FilaDato(widget.producto.montoLibre ? 'Referencia' : 'Teléfono', widget.referencia!),
              FilaDato('Cuenta', widget.cuenta.enmascarada),
              FilaDato('Total', dinero(widget.importe), destacado: true),
              const SizedBox(height: 16),
              TextField(
                controller: _pin,
                autofocus: true,
                obscureText: _oculto,
                maxLength: 4,
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                decoration: InputDecoration(
                  labelText: 'PIN de tu cuenta',
                  counterText: '',
                  prefixIcon: const Icon(Icons.pin_outlined),
                  suffixIcon: IconButton(
                    tooltip: _oculto ? 'Mostrar PIN' : 'Ocultar PIN',
                    icon: Icon(_oculto ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                    onPressed: () => setState(() => _oculto = !_oculto),
                  ),
                ),
                onChanged: (_) => setState(() {}),
                onSubmitted: (_) {
                  if (valido) Navigator.pop(context, _pin.text);
                },
              ),
              const SizedBox(height: 8),
              Text('Al confirmar se enviará la operación a GestoPago y se descontará de tu cuenta.',
                  style: t.textTheme.bodySmall?.copyWith(color: t.colorScheme.onSurfaceVariant)),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancelar')),
        FilledButton(
          onPressed: valido ? () => Navigator.pop(context, _pin.text) : null,
          child: Text('Pagar ${dinero(widget.importe)}'),
        ),
      ],
    );
  }
}
