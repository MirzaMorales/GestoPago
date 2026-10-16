# Pruebas de endpoints con Postman

Esta guía permite probar manualmente los endpoints REST de GestoPago desde Postman, tanto los casos de éxito como los errores esperados. Los ejemplos usan datos ficticios: no uses CURP, RFC, teléfonos, correos, contraseñas ni tokens reales en capturas o ambientes compartidos.

## 1. Preparar la aplicación

1. Inicia PostgreSQL y Redis con la configuración local de la aplicación. Flyway debe terminar de aplicar las migraciones.
2. Define `APP_JWT_SECRET` como una cadena aleatoria de al menos 32 bytes y arranca la aplicación desde la raíz:

   ```powershell
   $env:APP_JWT_SECRET = "<secreto-local-aleatorio-de-al-menos-32-bytes>"
   .\gradlew.bat bootRun
   ```

3. Espera a que Spring Boot confirme que la aplicación inició. La URL local usual es `http://localhost:8080`; ajusta el puerto si tu configuración es distinta.
4. En Postman, crea un environment, por ejemplo **GestoPago local**, con estas variables:

   | Variable | Valor inicial |
   |---|---|
   | `baseUrl` | `http://localhost:8080` |
   | `token` | *(vacío; se llena al iniciar sesión)* |
   | `clienteId` | *(vacío; se llena al registrar un cliente)* |
   | `usuarioId` | *(vacío; se llena al registrar un cliente)* |
   | `numeroCuenta` | *(vacío; se llena al registrar un cliente)* |
   | `expectedStatus` | `200` *(cámbialo según el caso que se esté probando)* |
   | `correoPrueba` | `prueba.gestopago@example.test` |
   | `passwordPrueba` | *(la contraseña ficticia elegida para el alta)* |

   Usa variables de colección o de entorno. No guardes credenciales personales ni secretos productivos en un environment sincronizado.

## 2. Convenciones de la colección

- Selecciona el environment **GestoPago local**.
- Crea carpetas **Preparación**, **Clientes**, **Cuentas**, **Autenticación y usuarios** y **Productos**.
- Para las rutas protegidas, en la pestaña **Authorization** selecciona **Bearer Token** y usa `{{token}}`. No escribas `Bearer` en el valor: Postman lo agrega.
- Para requests JSON, elige **Body > raw > JSON** y verifica el encabezado `Content-Type: application/json`.
- En **Tests** puedes agregar estas comprobaciones básicas. Ajusta `expectedStatus` por request:

  ```javascript
  pm.test("HTTP status esperado", function () {
    pm.response.to.have.status(Number(pm.variables.get("expectedStatus")));
  });
  ```

- En errores JSON, también se puede comprobar el contrato común:

  ```javascript
  pm.test("El error incluye status y mensaje", function () {
    const body = pm.response.json();
    pm.expect(body.status).to.eql(pm.response.code);
    pm.expect(body.mensaje).to.be.a("string").and.not.empty;
  });
  ```

  En las respuestas `204 No Content` no intentes parsear un body. Algunos errores de validación agregan `detalles` con los nombres de campos.

## 3. Preparación: registrar el cliente de prueba

`POST /clientes` y `POST /auth/login` son públicos. Los demás endpoints requieren un JWT activo de la aplicación. Registra primero un cliente ficticio; el registro crea domicilio, cliente, cuenta con saldo cero y usuario.

### Request de alta

- Método: `POST`
- URL: `{{baseUrl}}/clientes`
- Authorization: **No Auth**
- Body:

  ```json
  {
    "nombre": "Maria-Jose",
    "segundoNombre": "QA",
    "apellidoPaterno": "Gomez",
    "apellidoMaterno": "Prueba",
    "fechaNacimiento": "1985-01-01",
    "curp": "GODE850101HDFRRN09",
    "rfc": "GODE850101AB1",
    "sexo": "FEMENINO",
    "nacionalidad": "Mexicana",
    "estadoCivil": "SOLTERA",
    "correo": "{{correoPrueba}}",
    "telefonoMovil": "5512345678",
    "telefonoAlternativo": "5587654321",
    "ocupacion": "Analista QA",
    "empresa": "Empresa Ficticia",
    "ingresoMensual": 25000.50,
    "domicilio": {
      "calle": "Avenida de Prueba",
      "numeroExterior": "123",
      "numeroInterior": "4B",
      "colonia": "Centro",
      "municipio": "Cuauhtemoc",
      "estado": "Ciudad de Mexico",
      "codigoPostal": "06000",
      "pais": "Mexico"
    },
    "password": "PruebaSegura1!"
  }
  ```

`CURP` y `RFC` deben tener el formato indicado por la API, pero la aplicación no valida su autenticidad ante una autoridad. Si cambias `correoPrueba`, guarda la misma dirección en la variable de entorno. Los valores únicos solo pueden registrarse una vez en la base actual: para repetir la prueba usa un correo/CURP/RFC nuevos o una base de pruebas limpia.

### Resultado esperado y variables

- Esperado: `201 Created`.
- La respuesta usa la estructura uniforme `{ timestamp, status, mensaje, datos }`; en `datos` va el cliente y sus datos asociados. Copia `id` a `clienteId`, `usuario.id` a `usuarioId` y `cuentas[0].numeroCuenta` a `numeroCuenta`.
- La contraseña no debe aparecer como texto ni como hash en la respuesta.
- Si el registro ya existe: `409 Conflict`.
- Si un dato incumple formato, es menor de edad o falta un campo: `400 Bad Request`, con `timestamp`, `status`, `mensaje` y normalmente `detalles` (mensaje por campo).

Si la respuesta no incluye alguna relación, consulta las tablas de la base de datos local para obtener el ID/cuenta creados; no inventes esos valores.

Para capturar automáticamente los IDs de la respuesta `201`, agrega este script a la pestaña **Scripts > Post-response** del request de alta:

```javascript
if (pm.response.code === 201) {
  const cliente = pm.response.json().datos;
  pm.environment.set("clienteId", cliente.id);
  pm.environment.set("usuarioId", cliente.usuario.id);
  pm.environment.set("numeroCuenta", cliente.cuentas[0].numeroCuenta);
}
```

Conserva también la contraseña ficticia que escribiste en el body en `passwordPrueba`; no se devuelve en la respuesta.

## 4. Clientes

Todas estas rutas requieren `Bearer {{token}}`, excepto `POST /clientes`.

### 4.1 `POST /clientes` — registrar cliente

- Caso aprobado: usa el body completo de **Preparación** y valores únicos; debe responder `201`.
- Casos de error (repite el request cambiando un campo cada vez):
  - `telefonoMovil: "123"` o `domicilio.codigoPostal: "1234"` → `400`, error de formato.
  - `ingresoMensual: 0` o `10000000000000.001` → `400`, valor monetario fuera de regla/precisión.
  - `fechaNacimiento` de una persona menor de 18 años → `400`.
  - Omite `domicilio`, `nombre` o `password` → `400`.
  - Reutiliza correo, CURP o RFC registrados → `409`.

### 4.2 `POST /clientes/buscar` — búsqueda multicriterio (único endpoint de consulta)

Ningún endpoint usa `@PathVariable` ni `@RequestParam`: todo criterio viaja en el body JSON (`@RequestBody` + `@Valid`).

- Authorization: Bearer token. Body (todos los campos son opcionales y combinables):

  ```json
  {
    "id": 1,
    "curp": "GODE850101HDFRRN09",
    "rfc": "GODE850101AB1",
    "correo": "prueba",
    "nombre": "Maria",
    "apellido": "Gomez",
    "activo": true,
    "fechaInicio": "2025-01-01",
    "fechaFin": "2026-12-31",
    "numeroCuenta": "4001000000000000",
    "incluirDomicilio": true,
    "incluirCuentas": true,
    "incluirUsuario": true
  }
  ```

- Caso aprobado: `{ "nombre": "mar", "activo": true }` → `200`; la respuesta trae los clientes en `datos`. `{}` devuelve todos los clientes (solo ADMIN). Las banderas `incluir*` quitan secciones de la respuesta.
- Un CLIENTE solo puede consultar su propio `id` (`{ "id": {{clienteId}} }`); sin `id` o con un `id` ajeno → `403`.
- Casos de error:
  - `{ "curp": "INVALIDA" }`, `{ "numeroCuenta": "123" }` o `nombre` de 51 caracteres → `400` con `detalles` por campo.
  - `fechaInicio` posterior a `fechaFin` o fecha no ISO → `400`.
  - `{ "id": 99999 }` inexistente → `404`.
  - Body vacío o JSON mal formado → `400`. Sin token → `401`.

### 4.3 `PUT /clientes` — actualizar cliente

- Authorization: Bearer token. El `id` del cliente va en el body; ADMIN puede actualizar cualquiera y un CLIENTE solo el suyo.
- Body: actualización completa de los campos editables (no es PATCH); omite CURP, RFC y contraseña.

  ```json
  {
    "id": {{clienteId}},
    "nombre": "Maria-Jose",
    "segundoNombre": "QA Actualizada",
    "apellidoPaterno": "Gomez",
    "apellidoMaterno": "Prueba",
    "fechaNacimiento": "1985-01-01",
    "sexo": "FEMENINO",
    "nacionalidad": "Mexicana",
    "estadoCivil": "SOLTERA",
    "correo": "{{correoPrueba}}",
    "telefonoMovil": "5512345678",
    "telefonoAlternativo": "5587654321",
    "ocupacion": "Analista QA",
    "empresa": "Empresa Ficticia",
    "ingresoMensual": 26000.00,
    "domicilio": {
      "calle": "Avenida de Prueba",
      "numeroExterior": "123",
      "numeroInterior": "4B",
      "colonia": "Centro",
      "municipio": "Cuauhtemoc",
      "estado": "Ciudad de Mexico",
      "codigoPostal": "06000",
      "pais": "Mexico"
    }
  }
  ```

- Caso aprobado: `200` con los datos actualizados en `datos`.
- Casos de error:
  - Omite `id` u otro campo obligatorio, o manda teléfono/código postal inválido → `400`.
  - Ingreso fuera de límites o fecha de nacimiento de menor → `400`.
  - Usa un correo que pertenezca a otro registro → `409`.
  - `id` positivo inexistente (ADMIN) → `404`; `id` de otro cliente (CLIENTE) → `403`.
  - Sin token → `401`.

### 4.4 `DELETE /clientes` — baja lógica

- Body: `{ "id": {{clienteId}} }`. Solo ADMIN.
- Haz esta prueba **al final**: la baja desactiva el cliente y usuario y cambia cuentas activas a `INACTIVA`.
- Caso aprobado: `200` con `mensaje` de confirmación. Verifica luego que cliente/usuario estén inactivos y que el login ya no permita acceder (`403`).
- Casos de error: `id` inexistente → `404`; sin `id` o `id` no positivo → `400`; un CLIENTE → `403`; sin token → `401`.

## 5. Cuentas

Todas las rutas requieren Bearer token.

### 5.1 `POST /cuentas/buscar` — consultar cuentas

- Body (opcional y combinable): `{ "numeroCuenta": "{{numeroCuenta}}", "activas": true }`.
- Caso aprobado: `{}` (ADMIN) → `200` con todas las cuentas; `{ "activas": true }` → solo cuentas activas de clientes activos; `{ "numeroCuenta": "{{numeroCuenta}}" }` → esa cuenta (también para su dueño).
- Casos de error: `numeroCuenta` que no tenga 16 dígitos → `400`; 16 dígitos no registrados → `404`; un CLIENTE sin `numeroCuenta` o con cuenta ajena → `403`; sin token → `401`.

### 5.2 `POST /cuentas/saldo` — consultar saldo

- Body: `{ "numeroCuenta": "{{numeroCuenta}}" }`.
- Caso aprobado: `200`, con `numeroCuenta`, `saldo` y `estatus` en `datos`.
- Casos de error: `numeroCuenta` ausente o mal formado → `400`; cuenta inexistente → `404`; cuenta ajena (CLIENTE) → `403`; sin token → `401`.

## 6. Autenticación y usuarios

### 6.1 `POST /auth/login` — iniciar sesión

- Authorization: **No Auth**.
- Body aprobado:

  ```json
  {
    "correo": "{{correoPrueba}}",
    "password": "{{passwordPrueba}}"
  }
  ```

- Caso aprobado: `200`; copia `accessToken` a `token` y úsalo como Bearer Token en los requests protegidos. El alta inicial utiliza la contraseña enviada en `POST /clientes`.
- Casos de error:
  - Correo mal formado o contraseña vacía → `400`.
  - Correo que no existe o contraseña incorrecta → `401`.
  - Usuario o cliente inactivo → `403`.

Para guardar el JWT sin copiarlo manualmente, agrega este script a **Scripts > Post-response** del login:

```javascript
if (pm.response.code === 200) {
  pm.environment.set("token", pm.response.json().accessToken);
}
```

### 6.2 `POST /usuarios/consultar` — consultar usuario

- Body: `{ "id": {{usuarioId}} }`.
- Caso aprobado: `200` con correo/estado en `datos` y sin hash de contraseña.
- Casos de error: `id` inexistente → `404`; sin `id` o no positivo → `400`; usuario ajeno (CLIENTE) → `403`; sin token → `401`.

### 6.3 `PUT /usuarios/password` — cambiar contraseña

- Authorization: Bearer token de un usuario activo.
- Body:

  ```json
  {
    "id": {{usuarioId}},
    "passwordActual": "{{passwordPrueba}}",
    "nuevaPassword": "NuevaPruebaSegura2!"
  }
  ```

- Caso aprobado: `200 OK` con `mensaje` de confirmación. Luego intenta login con la contraseña nueva y comprueba `200`.
- Casos de error:
  - Contraseña actual incorrecta → `400`.
  - Contraseña nueva sin 8 caracteres, mayúscula, minúscula, número y carácter especial → `400`.
  - Usuario inexistente (ADMIN) → `404`; usuario ajeno (CLIENTE) → `403`; sin `id` → `400`; sin token → `401`.
- No guardes la contraseña real en una colección compartida. Al cambiarla, actualiza `passwordPrueba` localmente para poder completar las siguientes pruebas.

### 6.4 `DELETE /usuarios` — baja lógica de usuario

- Body: `{ "id": {{usuarioId}} }`. Solo ADMIN. Ejecuta esta prueba al final de las pruebas de usuario.
- Caso aprobado: `200` con `mensaje` de confirmación; el login posterior debe devolver `403`.
- Casos de error: usuario inexistente → `404`; sin `id` o no positivo → `400`; un CLIENTE → `403`; sin token → `401`.

## 7. Productos

Todas las rutas requieren JWT de la aplicación, aunque el parámetro `Authorization` del controlador `/getProductList` esté marcado opcional. El token que permite entrar a la API es el de `{{token}}`.

### 8.1 `GET /productos` — obtener productos del proveedor

- Caso aprobado: `GET {{baseUrl}}/productos` con Bearer `{{token}}`. Si el proveedor responde y el token de servicio está disponible, se espera `200`, con `codigo: 0` y `productos`.
- Casos de error:
  - Sin token/usuario de aplicación activo → `401`.
  - No hay token de servicio configurado/activo → `503 Service Unavailable`, con `codigo: 2`.
  - El proveedor falla o su respuesta no se puede procesar → `502 Bad Gateway`, con `codigo: 1`.
- La respuesta del proveedor puede cambiar; no fijes el contenido de `productos` en una aserción.

### 8.2 `GET /getProductList` — obtener productos usando la ruta compatible

- El endpoint requiere el JWT de la aplicación para superar Spring Security. Sin Authorization → `401`.
- **Limitación de implementación que afecta esta prueba:** el mismo encabezado `Authorization` se reenvía al proveedor como su token. Por tanto, no se puede omitir para activar el fallback al token guardado (Spring Security lo rechaza primero). Prueba `GET {{baseUrl}}/getProductList` con Bearer `{{token}}`; solo se espera `200` si ese JWT también es válido para el proveedor. En caso contrario se espera `502`. Para probar la consulta normal usando el token de servicio almacenado, usa `GET /productos`.
- Caso de error del proveedor: el proveedor no disponible o una respuesta inválida → `502`; token de servicio ausente → `503`.
- Un encabezado `Authorization` mayor de 4096 caracteres con un JWT inválido responde `401`: Spring Security lo rechaza antes de llegar al controlador.

### 8.3 `GET /productos/almacenados` — consultar caché o persistencia local

- Caso aprobado: `GET {{baseUrl}}/productos/almacenados` con Bearer `{{token}}`. Si hay datos, se espera `200`, `codigo: 0` y `origen` igual a `REDIS` o `POSTGRESQL`. Si ambos están vacíos, el servicio intenta obtenerlos en vivo.
- Casos de error: sin JWT → `401`; si el fallback externo falla, `502` o `503` según la causa.
- Para probar los orígenes, ejecuta antes `GET /productos` y vuelve a consultar los almacenados.

## 8. Pruebas transversales de errores

Ejecuta estas solicitudes contra un endpoint protegido, por ejemplo `POST /clientes/buscar` con body `{}`:

| Prueba | Configuración/request | Esperado |
|---|---|---|
| Falta autenticación | Sin `Authorization` | `401`, JSON con `status` y `mensaje`. |
| Token inválido | Bearer `no-es-un-jwt` | `401`, JSON con `status` y `mensaje`. |
| JSON mal formado | En un endpoint JSON, envía `{"nombre":` | `400`, mensaje de cuerpo inválido. |
| Tipo de contenido incorrecto | Envía texto plano a `POST /clientes` | `415`. |
| Método HTTP no permitido | `PATCH {{baseUrl}}/clientes` | `405`. |
| Recurso inexistente | ID/cuenta/usuario válido en formato pero inexistente | `404`. |
| Error inesperado | Solo en ambiente de pruebas controlado; no provoques fallos en datos productivos | `500` con mensaje genérico, sin detalles internos. |

Todas las respuestas de error de la aplicación usan `timestamp`, `status`, `error` y `mensaje`; errores de validación también incluyen `detalles`. Las fallas del servicio externo de productos conservan además su contrato de producto (`codigo`, `mensaje`, `productos`) y usan HTTP `502`/`503`.

## 9. Orden recomendado de ejecución

1. `POST /clientes`, conservar IDs/cuenta y contraseña en el environment local.
2. `POST /auth/login`, guardar `accessToken` en `token`.
3. Validar búsquedas, consultas de cliente/cuenta/usuario y lista de cuentas activas.
4. Probar `PUT /clientes`, `PUT /usuarios/password` y los endpoints de productos.
5. Probar casos de error sin modificar datos válidos; usa entradas mal formadas independientes.
6. Ejecutar `DELETE /clientes` y `DELETE /usuarios` (con el `id` en el body) al final y comprobar la revocación lógica.
7. Elimina el environment de pruebas o limpia las variables de token/contraseña al terminar. No ejecutes estas bajas contra IDs de producción.
