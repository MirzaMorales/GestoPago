# Seguridad del aplicativo GestoPago

Controles de seguridad del backend (Spring Boot + **Spring Security**) y de la app Flutter (web y móvil),
con la evidencia de los ataques probados.

## 1. Autenticación y autorización (Spring Security)

| Control | Implementación |
|---|---|
| Autenticación sin estado | JWT firmado (HS256) en `Authorization: Bearer`; un token alterado o con `alg: none` responde 401. |
| Doble factor (2FA) | Código de 6 dígitos por correo, validado **en el servidor** (`/auth/verificar-2fa`), con vigencia e intentos limitados. |
| Sesión única | Redis guarda el `jti` vigente por usuario; si hay otra sesión responde `409 SESION_ACTIVA` y el token anterior deja de servir al reemplazarla. |
| Expiración | JWT y sesión expiran a los 60 min. |
| Control de acceso | `@PreAuthorize` por rol (ADMIN / CLIENTE) y por dueño del recurso (`@accesos.esDuenoDeCuenta`, `esClienteId`). |
| Contraseñas y PIN | BCrypt (hash irreversible); longitud máxima de 128 caracteres. |
| Fuerza bruta | Bloqueo por correo tras 5 intentos fallidos de login, bloqueo de PIN y límite por IP (sección 3). |
| Recuperar contraseña | Código de un solo uso por correo (hash en Redis, vigencia e intentos limitados). La respuesta no revela si el correo existe; un código de recuperación no sirve para iniciar sesión (ni al revés). Al restablecer se cierran las sesiones abiertas. |

### ¿Por qué Spring Security y no Keycloak?

La consigna permite Keycloak **o** Spring Security. Elegimos Spring Security porque:

- Ya cubre todo lo que pide la unidad: JWT, roles, 2FA validado en el servidor, sesión única entre web y móvil y expiración.
- Keycloak es un servidor aparte (Java, más de 1 GB de RAM). No cabe en el plan gratuito de Render (512 MB) y agregaría otro punto de falla.
- La sesión única entre plataformas y el 2FA por correo ya están integrados en el flujo de login. Con Keycloak habría que rehacerlos con sus propias extensiones.

## 2. Validación de entradas (HTML, scripts, inyección)

- **Listas blancas por campo** (`PatronesSeguros`):
  - **texto libre** (ocupación, empresa, calle, números): letras, números, espacios y `. , ' - # / & ( )`;
  - **nombres y catálogos**: solo letras;
  - **correo**: sin comillas ni comentarios;
  - **colonia, municipio y estado**: además deben existir en el catálogo oficial SEPOMEX.
- Se rechaza cualquier `<script>`, `<img onerror>`, `{"$ne":…}`, `${jndi:…}`, caracteres de control, `;`, `|`, etc.
- Nacionalidad: solo mexicana (el servicio y la CURP lo exigen). Abonos de saldo: máximo $50,000 por operación.
- Tipos y formatos estrictos: CURP, RFC, teléfono, CP, montos (`@Digits`), fechas, longitudes máximas.
- **Campos desconocidos** en el JSON se rechazan (por ejemplo `"rol":"ADMIN"` → 400 "Campo no permitido").
- **SQL injection**: todas las consultas son JPA / JPQL parametrizadas; no se concatena SQL.
- **XSS**: la API solo devuelve JSON con `Content-Security-Policy: default-src 'none'`. Flutter dibuja los datos como texto (no interpreta HTML) y además filtra la captura con las mismas listas blancas.

## 3. Protección contra abuso y denegación de servicio

| Control | Valor |
|---|---|
| Tamaño máximo del cuerpo | 32 KB → `413` (también en envíos sin `Content-Length`) |
| Tamaño máximo de cabeceras | 16 KB |
| Límite por IP: login | 10 / minuto → `429` con `Retry-After` |
| Límite por IP: verificación 2FA | 15 / minuto |
| Límite por IP: registro | 10 / hora |
| Límite por IP: recuperar contraseña | 10 / hora |
| Límite por IP: catálogo público | 120 / minuto |
| Límite por IP: resto de la API | 300 / minuto |

Los límites se ajustan con variables de entorno (`APP_LIMITE_*`). La IP real se toma de Cloudflare
(`CF-Connecting-IP`), que el cliente no puede falsificar.

## 4. Manejo de errores sin exponer información

- Respuesta uniforme `{status, mensaje, detalles}` sin trazas, nombres de clases ni SQL.
- Las URLs inválidas que Tomcat rechaza antes de llegar a Spring responden JSON (`ErroresTomcatJsonValve`) en lugar de su página HTML con la versión del servidor.
- Los logs no registran contraseñas, PIN, tokens ni la URL de autenticación del proveedor.

## 5. Cabeceras HTTP de seguridad

`Content-Security-Policy`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
`Strict-Transport-Security`, `Referrer-Policy: no-referrer`, `Permissions-Policy`. CORS limitado al dominio de la web.

## 6. Comunicación y datos

- HTTPS en la web, la API y el proveedor (TLS); el payload hacia GestoPago va cifrado con AES-256.
- En la app, el token se guarda en almacenamiento seguro (Keystore / Keychain) y se borra al cerrar sesión; un 401 regresa al login.
- Aviso de Privacidad y Términos aceptados expresamente; el servidor guarda versión y fecha de aceptación.

## 7. Evidencia: ataques probados

| Ataque | Resultado |
|---|---|
| `<script>` / `<img onerror>` en ocupación, calle, sexo… | 400 con el campo y el motivo |
| JSON malformado, tipos incorrectos, XML | 400 / 415 |
| Campo extra `"rol":"ADMIN"` | 400 "Campo no permitido" |
| Cuerpo de 2 MB | 413 |
| Contraseña de 5,000 caracteres | 400 |
| `<script>` en la URL | 400 en JSON (sin página de Tomcat) |
| JWT alterado / `alg: none` | 401 |
| Usuario usando datos de otro (cuenta, historial) | 403 |
| Logins seguidos desde la misma IP por encima del límite | 429 con `Retry-After`; no se procesan |
| SQL injection en parámetros | Consultas parametrizadas; además lo bloquea el firewall de Render |

Pruebas automatizadas: `./gradlew test` (backend) y `flutter test` (app). Ver `EntradasMaliciosasValidationTest`,
`FiltrosSeguridadTest`, `AccesoSesionFlujoWebTest` y `CatalogoWebTest`.
