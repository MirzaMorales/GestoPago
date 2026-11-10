# GestoPago App (Flutter: web y móvil)

Una sola base de código para la aplicación **web** y la **móvil** (Android/iOS). Consume el backend
de este repositorio (`https://gestopago-bmb0.onrender.com`), sin duplicar lógica de negocio.

## Funcionalidad

| Módulo | Pantalla | Endpoints |
|---|---|---|
| Autenticación | Login → código 2FA; aviso de sesión activa en otra plataforma | `POST /auth/login`, `POST /auth/verificar-2fa`, `POST /auth/logout` |
| Catálogo | Servicios con logo, búsqueda, categorías, detalle y precio | `GET /productos/almacenados` |
| Compra | Datos → confirmación con PIN → resultado (exitosa, rechazada, pendiente, error de comunicación) | `POST /pagos/servicio` (con `Idempotency-Key`), `POST /pagos/estado` |
| Historial | Compras exitosas paginadas y su detalle | `POST /pagos/historial`, `POST /pagos/historial/detalle` |
| Mi cuenta | Cuentas y saldo, PIN de compras, cerrar sesión | `POST /clientes/buscar`, `POST /cuentas/pin` |

- La plataforma se envía como `WEB` (navegador) o `MOVIL` (app). Si el servidor responde `409 SESION_ACTIVA`,
  la app pregunta si se cierra la otra sesión y reintenta con `cerrarSesionAnterior: true`.
- Cualquier `401` en una petición autenticada (sesión expirada o cerrada desde otra plataforma) borra el
  token y regresa al login.
- El token se guarda con `flutter_secure_storage` (Keystore/Keychain en móvil).

## Ejecutar

```bash
flutter pub get
flutter run -d chrome --web-port 5173      # web (el backend acepta el origen http://localhost:5173)
flutter run                                # dispositivo o emulador Android
```

Otro backend: `--dart-define=API_URL=http://10.0.2.2:8080` (emulador Android contra el backend local).

## Compilar

```bash
flutter build web --release   # salida en build/web (sitio estático)
flutter build apk --release   # build/app/outputs/flutter-apk/app-release.apk
```

## Publicar la web en Render (Static Site)

| Campo | Valor |
|---|---|
| Branch | `main` |
| Root Directory | `app_flutter` |
| Build Command | `bash render-build.sh` |
| Publish Directory | `build/web` |

`render-build.sh` descarga Flutter (Render no lo trae) y compila con `API_URL` (variable opcional; por
defecto el backend de producción). Después agrega la URL del sitio en el backend de Render a la variable
`APP_CORS_ALLOWED_ORIGINS` (separadas por coma, por ejemplo `https://mi-web.onrender.com,http://localhost:5173`).

## Pruebas

```bash
flutter analyze
flutter test
```
