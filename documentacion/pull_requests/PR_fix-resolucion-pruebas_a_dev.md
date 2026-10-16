# PR: `fix/resolucion-pruebas` → `dev`

**Título sugerido:** `refactor(api): endpoints sin PathVariable/RequestParam, manejo global de errores, roles y retiro de Personas`

## 1. Resumen

Este PR deja la API alineada con la especificación del profesor ("API REST para la administración de usuarios con Java y Spring Boot") y con sus observaciones sobre exponer datos en la URL:

1. **Ningún endpoint usa `@PathVariable` ni `@RequestParam`.** Identificadores (`id`, `numeroCuenta`) y criterios de búsqueda viajan en el body JSON (`@RequestBody` + `@Valid`), nunca en la ruta o query string.
2. **Un único endpoint de consulta por recurso** (`/clientes/buscar`, `/cuentas/buscar`) con filtros opcionales y combinables, en lugar de un endpoint por campo.
3. **Estructura de respuesta uniforme** para éxitos y errores (`GenericResponse`).
4. **Manejo centralizado de excepciones** con `@RestControllerAdvice` (`ValidadorCampos` + `GlobalExceptionHandler`), sin `try-catch` ni validaciones repetitivas en controladores.
5. **Se eliminó el módulo Personas** (endpoints, servicio, repositorio, entidad, DTOs, excepción y tabla).
6. **Control de acceso por rol** (`ADMIN` / `CLIENTE`) y por propiedad del recurso.

## 2. Cambios en la API (breaking changes)

Todos los consumidores (Postman, front, scripts) deben migrar a las nuevas rutas.

| Antes | Ahora | Acceso |
|---|---|---|
| `POST /clientes` | `POST /clientes` (sin cambios de ruta; respuesta envuelta en `GenericResponse`) | Público |
| `GET /clientes?curp=..&rfc=..` | `POST /clientes/buscar` con `BuscaClienteRequest` | ADMIN, o cliente consultando su propio `id` |
| `GET /clientes/{id}` | `POST /clientes/buscar` con `{ "id": 1 }` | ADMIN o dueño |
| `PUT /clientes/{id}` | `PUT /clientes` (el `id` va en el body) | ADMIN o dueño |
| `DELETE /clientes/{id}` | `DELETE /clientes` con `{ "id": 1 }` | ADMIN |
| `GET /cuentas`, `GET /cuentas?activas=true`, `GET /cuentas/{n}` | `POST /cuentas/buscar` con `{ "numeroCuenta", "activas" }` | ADMIN, o dueño indicando `numeroCuenta` |
| `GET /cuentas/{n}/saldo` | `POST /cuentas/saldo` con `{ "numeroCuenta" }` | ADMIN o dueño |
| `GET /usuarios/{id}` | `POST /usuarios/consultar` con `{ "id" }` | ADMIN o el propio usuario |
| `PUT /usuarios/{id}/password` | `PUT /usuarios/password` (`id`, `passwordActual`, `nuevaPassword`) | ADMIN o el propio usuario |
| `DELETE /usuarios/{id}` | `DELETE /usuarios` con `{ "id" }` | ADMIN |
| `/personas/**` | **Eliminado** | — |
| `POST /auth/login`, `/productos`, `/getProductList`, `/productos/almacenados` | Sin cambios | — |

Las rutas anteriores con `{id}` / `{numeroCuenta}` ahora responden `404`.

> Se usa `POST` para consultar porque la especificación exige criterios en `@RequestBody` y un `GET` con body no es confiable en clientes HTTP, proxies ni navegadores.

### Formato de respuesta uniforme

Éxito:

```json
{
  "timestamp": "2026-10-15T18:06:21.60",
  "status": 200,
  "mensaje": "Consulta realizada correctamente",
  "datos": [ { "...": "..." } ]
}
```

Error de validación (`400`):

```json
{
  "timestamp": "2026-10-15T18:06:22.12",
  "status": 400,
  "mensaje": "Error de validación en la solicitud",
  "detalles": { "curp": "Formato de CURP inválido" }
}
```

Los campos nulos se omiten (`@JsonInclude(NON_NULL)`). Antes existían dos formatos de error distintos (`ErrorResponse` y el `GenericResponse` de `ValidadorCampos`); ahora hay uno solo. `ErrorResponse` se eliminó y `ApiErrorResponseWriter` (usado también por los handlers de seguridad 401/403) construye el mismo envoltorio.

Cambio notable en cuerpos de respuesta: los `DELETE` y `PUT /usuarios/password` pasan de `204`/`200` sin body a `200` con `mensaje` de confirmación.

## 3. Detalle técnico

### 3.1 DTOs de entrada (nuevos)
Todos con Bean Validation y mensajes en español:
- `BuscaClienteRequest`: `id`, `curp`, `rfc`, `correo`, `nombre`, `apellido`, `activo`, `fechaInicio`, `fechaFin`, `numeroCuenta` + banderas `incluirDomicilio`, `incluirCuentas`, `incluirUsuario` (default `true`).
- `BuscaCuentaRequest`: `numeroCuenta` (16 dígitos), `activas`.
- `ConsultaSaldoRequest`: `numeroCuenta` obligatorio, 16 dígitos.
- `ClienteIdRequest` / `UsuarioIdRequest`: `id` obligatorio y positivo.
- `ActualizaClienteRequest` y `CambiaPasswordRequest`: se agrega `id` (`@NotNull @Positive`).

Se impide que el cliente toque campos controlados por el servidor: los request no incluyen `activo`, `rol`, fechas, saldo, estatus ni CURP/RFC en la actualización.

### 3.2 Controladores
- `ClienteController`, `CuentaController`, `UsuarioController` reescritos: sin `@PathVariable`/`@RequestParam`, sin `@Validated` a nivel clase (ya no hay parámetros sueltos), sin `try-catch`, devuelven `GenericResponse<DTO>`; nunca entidades JPA.
- `/clientes/buscar`: si el usuario no es ADMIN, se **ignoran todos los criterios salvo `id`** y las banderas, para que un cliente no pueda enumerar a otros combinando filtros.
- Autorización con `@PreAuthorize` evaluando el body (`#request.id`, `#request.numeroCuenta`) y el bean `@accesos`.

### 3.3 Servicios y repositorio
- `ClienteService`: `buscarClientesDinamico(BuscaClienteRequest)` y `actualizarCliente(ActualizaClienteRequest)`; se elimina `obtenerClientePorId` (absorbido por la búsqueda con `id`). Si se busca por `id` y no existe → `ClienteNoEncontradoException` (404).
- `CuentaService`: `buscarCuentas(BuscaCuentaRequest)` reemplaza `obtenerCuentaPorNumero`, `obtenerTodasLasCuentas`, `obtenerCuentasActivas`.
- `UsuarioService.cambiarPassword(CambiaPasswordRequest)` toma el `id` del DTO.
- `ClienteSpecification.buscarConFiltros` recibe `id` como primer predicado (consulta dinámica con JPA Specifications).

### 3.4 Manejo global de excepciones
- `ValidadorCampos` (`@RestControllerAdvice`, `@Order(HIGHEST_PRECEDENCE)`): atrapa `MethodArgumentNotValidException` (DTO con `@Valid`) y `ConstraintViolationException`; devuelve `400` con `detalles` por campo.
- `GlobalExceptionHandler`: mapea excepciones de negocio a códigos HTTP — `404` (`Cliente/Cuenta/UsuarioNoEncontrada`), `409` (`CurpDuplicada`, `RfcDuplicado`, `CorreoDuplicado`, `ClienteYaRegistrado`, `DataIntegrityViolation`), `401` (`CredencialesInvalidas`), `403` (`UsuarioInactivo`, `AccessDenied`), `400` (`ContrasenaInvalida`, `ValidacionNegocio`, JSON ilegible, tipo inválido), `405`, `406`, `415` y `500` genérico (el detalle real solo se escribe en el log del servidor; nunca se devuelve SQL ni stacktrace).

### 3.5 Seguridad y roles
- Nuevo enum `Rol` y columna `rol` en `usuarios` (migración `V6__add_rol_usuarios.sql`).
- `AdminInitializer`: crea el ADMIN inicial si se definen `APP_ADMIN_CORREO` y `APP_ADMIN_PASSWORD`.
- `AccesosService` (`@accesos`): `esClienteId`, `esUsuarioId`, `esDuenoDeCuenta`, usados desde `@PreAuthorize`.
- `JwtAuthenticationFilter` y `SecurityConfig` actualizados para cargar el rol como authority (`ROLE_ADMIN` / `ROLE_CLIENTE`).

### 3.6 Eliminación del módulo Personas
Eliminados: `PersonaController`, `PersonaService`, `PersonasServiceImpl`, `PersonasRepository`, entidad `Personas`, `PersonaNoEncontradaException`, `PersonasRequest`, `EliminaPersonaRequest`, `PersonaResponse`. Migración `V7__drop_personas.sql` elimina la tabla.

> ⚠️ `V7` borra la tabla `personas` y sus datos. Respaldar antes de desplegar si hay información que conservar.

### 3.7 Documentación
- `documentacion/pruebas_endpoints_postman.md` y `documentacion/integracion_clientes.md` actualizados a las nuevas rutas, bodies y formato de respuesta.
- Se borró `documentacion/evidencias_endpoints/Evidencias_Pruebas_Endpoints_GestoPago.docx` (y su archivo temporal `~$...docx`): describía los endpoints anteriores y debe regenerarse.

## 4. Cómo se verificó

- `./gradlew compileJava compileTestJava` sin errores.
- `./gradlew test`: todas las pruebas pasan (se actualizó `PasswordValidationTest` porque `CambiaPasswordRequest` ahora exige `id`).
- **Prueba de integración manual** con la aplicación levantada sobre un PostgreSQL desechable (Flyway V1–V7 aplicadas desde cero, ADMIN inicial creado): **36 solicitudes con el resultado esperado**.

| Escenario | Resultado |
|---|---|
| Registro público de cliente | 201 |
| Registro con campos faltantes/ inválidos | 400 con `detalles` por campo |
| Registro con CURP repetida | 409 |
| Login admin y cliente | 200 |
| Buscar clientes (nombre + activo, id, banderas `incluir*`) como ADMIN | 200 |
| Buscar `id` inexistente | 404 |
| CURP inválida / fechas invertidas / body no JSON | 400 |
| Sin token | 401 |
| Cliente consulta su propio `id` | 200 |
| Cliente consulta `id` ajeno o sin `id` | 403 |
| Cliente actualiza su perfil / actualizar sin `id` | 200 / 400 |
| Cuentas: listar todas, solo activas, por número (ADMIN) | 200 |
| Cliente consulta su cuenta / lista todas | 200 / 403 |
| `numeroCuenta` de 3 dígitos o ausente | 400 |
| Saldo propio / cuenta inexistente (ADMIN) | 200 / 404 |
| Usuario consulta su propio registro / uno ajeno | 200 / 403 |
| Cambio de contraseña con actual incorrecta / correcta | 400 / 200 |
| Cliente intenta dar de baja / ADMIN da de baja / sin `id` / inexistente | 403 / 200 / 400 / 404 |
| `GET /clientes/{id}` (ruta vieja) | 404 |
| `GET /clientes/buscar` (método incorrecto) | 405 |

## 5. Checklist contra la especificación del profesor

- [x] Separación Controller / Service / Repository / DTO / Entity.
- [x] Request DTO para entrada y Response DTO para salida.
- [x] Bean Validation (`@NotNull`, `@NotBlank`, `@Email`, `@Size`, `@Pattern`, `@Positive`, `@Past`, `@DecimalMin`, `@Digits`…).
- [x] `@Valid` en todos los parámetros DTO de los controladores.
- [x] `@RestControllerAdvice` con varios `@ExceptionHandler`.
- [x] Excepciones propias de negocio (usuario/cliente/cuenta inexistente, correo/CURP/RFC duplicado…).
- [x] Búsqueda por distintos criterios combinables mediante un único endpoint.
- [x] Spring Data JPA (repositories + Specifications).
- [x] El cliente no puede modificar campos controlados por el servidor.
- [x] Códigos de estado adecuados por operación.
- [x] Estructura uniforme de respuestas exitosas y de error.
- [x] Sin `@PathVariable`. Sin `@RequestParam`.
- [x] Criterios de búsqueda en `@RequestBody`.
- [x] Sin `try-catch` en controladores; sin validaciones repetitivas en controladores.
- [x] No se devuelven ni reciben entidades JPA.
- [x] Mensajes de error específicos; sin trazas, SQL ni detalles internos hacia el cliente.

## 6. Notas para el revisor / riesgos

- **Breaking change** de rutas y de formato de respuesta (datos ahora bajo `datos`). Los clientes deben actualizarse.
- Migraciones nuevas: `V6` (agrega `rol`) y `V7` (elimina `personas`). Variables de entorno para el ADMIN inicial: `APP_ADMIN_CORREO`, `APP_ADMIN_PASSWORD`; el secreto JWT sigue siendo `APP_JWT_SECRET`.
- Comportamiento heredado que no se modificó: si una búsqueda de ADMIN sin `id` no tiene coincidencias, devuelve todos los clientes en lugar de lista vacía.
- Deuda conocida (previa a este PR): la respuesta de `POST /clientes` trae `cuentas: []` y `usuario: null` aunque ambos se crean; aparecen al consultar con `/clientes/buscar`.
- Las evidencias en `integracion_clientes.md` (E05–E08) describen capturas de los endpoints anteriores y deben volver a tomarse.
- Productos (`/productos`, `/getProductList`) no cambió: usa `@RequestHeader` para el token, que no está prohibido por la especificación.
