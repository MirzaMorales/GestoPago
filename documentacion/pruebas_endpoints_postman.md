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
   | `nombrePersona` | `Postman Persona QA` |

   Usa variables de colección o de entorno. No guardes credenciales personales ni secretos productivos en un environment sincronizado.

## 2. Convenciones de la colección

- Selecciona el environment **GestoPago local**.
- Crea carpetas **Preparación**, **Clientes**, **Cuentas**, **Autenticación y usuarios**, **Personas heredadas** y **Productos**.
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
- La respuesta contiene el cliente y sus datos asociados; copia `id` a `clienteId`, `usuario.id` a `usuarioId` y `cuentas[0].numeroCuenta` a `numeroCuenta`.
- La contraseña no debe aparecer como texto ni como hash en la respuesta.
- Si el registro ya existe: `409 Conflict`.
- Si un dato incumple formato, es menor de edad o falta un campo: `400 Bad Request`, con `status`, `error`, `mensaje` y normalmente `detalles`.

Si la respuesta no incluye alguna relación, consulta las tablas de la base de datos local para obtener el ID/cuenta creados; no inventes esos valores.

Para capturar automáticamente los IDs de la respuesta `201`, agrega este script a la pestaña **Scripts > Post-response** del request de alta:

```javascript
if (pm.response.code === 201) {
  const cliente = pm.response.json();
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

### 4.2 `GET /clientes` — listar clientes

- Authorization: Bearer token.
- Caso aprobado: `GET {{baseUrl}}/clientes` → `200` y lista JSON. Filtros aprobados:
  - `GET {{baseUrl}}/clientes?activos=true`
  - `GET {{baseUrl}}/clientes?fechaInicio=2025-01-01&fechaFin=2026-12-31`
- Casos de error:
  - `GET {{baseUrl}}/clientes?fechaInicio=2025-01-01` (falta fecha final) → `400`.
  - `GET {{baseUrl}}/clientes?fechaInicio=2026-12-31&fechaFin=2025-01-01` → `400`.
  - `GET {{baseUrl}}/clientes?fechaInicio=no-es-fecha&fechaFin=2026-12-31` → `400`.
  - `GET {{baseUrl}}/clientes?activos=tal-vez` → `400`.
  - Sin token o con token inválido → `401`.

### 4.3 `GET /clientes/buscar` — búsqueda multicriterio

- Caso aprobado: `GET {{baseUrl}}/clientes/buscar?correo={{correoPrueba}}&activo=true`; también se puede probar con `curp`, `rfc`, `nombre`, `apellido`, `numeroCuenta`, `fechaInicio` y/o `fechaFin`. Los criterios se pueden combinar.
- `fechaInicio` y `fechaFin` pueden enviarse individualmente en esta búsqueda; si se mandan ambas, inicio debe ser anterior o igual a fin.
- Casos de error:
  - `GET {{baseUrl}}/clientes/buscar?curp=INVALIDA` → `400`.
  - `GET {{baseUrl}}/clientes/buscar?numeroCuenta=123` → `400`.
  - `GET {{baseUrl}}/clientes/buscar?nombre=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA` (51 caracteres) → `400`.
  - Fechas invertidas o fecha no ISO → `400`.
  - Sin token o token inválido → `401`.
- Una búsqueda válida sin coincidencias responde `200` con `[]`, no `404`.

### 4.4 `GET /clientes/{{clienteId}}` — consultar por ID

- Caso aprobado: usa el ID guardado en `clienteId` → `200` con cliente.
- Error: usa un ID positivo inexistente, por ejemplo `9223372036854770000` si no existe → `404`; ID `0` o negativo → `400`; sin token → `401`.

### 4.5 `GET /clientes/curp/{{curp}}` — consultar por CURP

- Caso aprobado: `GET {{baseUrl}}/clientes/curp/GODE850101HDFRRN09` → `200`.
- Error: CURP con forma incorrecta, por ejemplo `ABC` → `400`; CURP válida de formato pero no registrada → `404`; sin token → `401`.

### 4.6 `GET /clientes/rfc/{{rfc}}` — consultar por RFC

- Caso aprobado: `GET {{baseUrl}}/clientes/rfc/GODE850101AB1` → `200`.
- Error: RFC con forma incorrecta, por ejemplo `ABC` → `400`; RFC válida de formato pero no registrada → `404`; sin token → `401`.

### 4.7 `GET /clientes/correo/{{correo}}` — consultar por correo

- Caso aprobado: `GET {{baseUrl}}/clientes/correo/prueba.gestopago%40example.test` → `200`. Codifica `@` como `%40` al usarlo en este segmento de ruta.
- Error: texto que no sea correo o correo de más de 100 caracteres → `400`; correo válido sin registro → `404`; sin token → `401`.

### 4.8 `GET /clientes/cuenta/{{numeroCuenta}}` — consultar cliente asociado a cuenta

- Caso aprobado: `GET {{baseUrl}}/clientes/cuenta/{{numeroCuenta}}` → `200`.
- Error: número que no tenga exactamente 16 dígitos → `400`; número válido no asociado → `404`; sin token → `401`.

### 4.9 `PUT /clientes/{{clienteId}}` — actualizar cliente

- Authorization: Bearer token.
- Body: es una actualización completa de los campos editables (no es PATCH); omite CURP, RFC y contraseña.

  ```json
  {
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

- Caso aprobado: `200` con los datos actualizados.
- Casos de error:
  - Omite un campo obligatorio o manda teléfono/código postal inválido → `400`.
  - Ingreso fuera de límites o fecha de nacimiento de menor → `400`.
  - Usa un correo que pertenezca a otro registro → `409`.
  - ID positivo inexistente → `404`; ID no positivo → `400`.
  - Sin token → `401`.

### 4.10 `DELETE /clientes/{{clienteId}}` — baja lógica

- Haz esta prueba **al final**: la baja desactiva el cliente y usuario y cambia cuentas activas a `INACTIVA`.
- Caso aprobado: `DELETE {{baseUrl}}/clientes/{{clienteId}}` → `204 No Content`. Verifica luego que el cliente/usuario estén inactivos y que el login y token anterior ya no permitan acceder.
- Casos de error: ID inexistente → `404`; ID cero/negativo → `400`; sin token → `401`.
- Ejecutar la baja otra vez para el mismo ID vuelve a responder `204` (la operación es idempotente a nivel de estado).

## 5. Cuentas

Todas las rutas requieren Bearer token.

### 5.1 `GET /cuentas/activas` — listar cuentas activas

- Caso aprobado: `GET {{baseUrl}}/cuentas/activas` → `200` y lista de cuentas activas de clientes activos.
- Caso de error: sin token o token inválido → `401`. Si no hay cuentas, el resultado esperado es `200` con `[]`.

### 5.2 `GET /cuentas/{{numeroCuenta}}` — consultar cuenta

- Caso aprobado: usa `{{numeroCuenta}}` → `200` con ID, saldo y estatus.
- Casos de error: número con longitud/formato incorrectos → `400`; 16 dígitos no registrados → `404`; sin token → `401`.

### 5.3 `GET /cuentas/{{numeroCuenta}}/saldo` — consultar saldo

- Caso aprobado: `GET {{baseUrl}}/cuentas/{{numeroCuenta}}/saldo` → `200`, con `numeroCuenta`, `saldo` y `estatus`.
- Casos de error: número mal formado → `400`; cuenta inexistente → `404`; sin token → `401`.

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

### 6.2 `GET /usuarios/{{usuarioId}}` — consultar usuario

- Caso aprobado: `GET {{baseUrl}}/usuarios/{{usuarioId}}` → `200` con correo/estado y sin hash de contraseña.
- Casos de error: ID inexistente → `404`; ID cero/negativo → `400`; sin token → `401`.

### 6.3 `PUT /usuarios/{{usuarioId}}/password` — cambiar contraseña

- Authorization: Bearer token de un usuario activo.
- Body:

  ```json
  {
    "passwordActual": "{{passwordPrueba}}",
    "nuevaPassword": "NuevaPruebaSegura2!"
  }
  ```

- Caso aprobado: `200 OK` sin body. Luego intenta login con la contraseña nueva y comprueba `200`.
- Casos de error:
  - Contraseña actual incorrecta → `400`.
  - Contraseña nueva sin 8 caracteres, mayúscula, minúscula, número y carácter especial → `400`.
  - Usuario inexistente → `404`; ID no positivo → `400`; sin token → `401`.
- No guardes la contraseña real en una colección compartida. Al cambiarla, actualiza `passwordPrueba` localmente para poder completar las siguientes pruebas.

### 6.4 `DELETE /usuarios/{{usuarioId}}` — baja lógica de usuario

- Ejecuta esta prueba al final de las pruebas de usuario. Desactiva el usuario asociado.
- Caso aprobado: `204 No Content`; el login posterior debe devolver `403`.
- Casos de error: usuario inexistente → `404`; ID no positivo → `400`; sin token → `401`.

## 7. Personas heredadas

Estas rutas también están protegidas por JWT. Los campos admitidos son `nombre`, `apellidoP` y `apellidoMaterno`; cada uno es obligatorio y admite hasta 100 caracteres. Usa nombres distintos para las pruebas de creación/actualización/eliminación.

### 7.1 `POST /personas` — crear persona

- Body:

  ```json
  {
    "nombre": "{{nombrePersona}}",
    "apellidoP": "Apellido Paterno",
    "apellidoMaterno": "Apellido Materno"
  }
  ```

- Caso aprobado: `201 Created`; el JSON incluye `codigo: 0` y `mensaje`.
- Casos de error: nombre/apellido vacío, ausente o de más de 100 caracteres → `400`; sin token → `401`.

### 7.2 `PUT /personasActualiza` — actualizar persona

- Body: enviar los tres campos; `nombre` identifica el registro existente.

  ```json
  {
    "nombre": "{{nombrePersona}}",
    "apellidoP": "Apellido Paterno Actualizado",
    "apellidoMaterno": "Apellido Materno Actualizado"
  }
  ```

- Caso aprobado: `200 OK`, `codigo: 0`.
- Casos de error: persona no encontrada por nombre → `404`; campo vacío/ausente o más de 100 caracteres → `400`; sin token → `401`.

### 7.3 `PUT /personasElimina` — eliminar persona heredada

- Body:

  ```json
  {
    "nombre": "{{nombrePersona}}"
  }
  ```

- Caso aprobado: `200 OK`, `codigo: 0`.
- Casos de error: nombre vacío/ausente o mayor de 100 caracteres → `400`; persona no encontrada → `404`; sin token → `401`.
- Esta ruta elimina físicamente un registro de la tabla heredada `personas`; no confundirla con la baja lógica de clientes.

## 8. Productos

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
- Un encabezado `Authorization` mayor de 4096 caracteres debe responder `400`.

### 8.3 `GET /productos/almacenados` — consultar caché o persistencia local

- Caso aprobado: `GET {{baseUrl}}/productos/almacenados` con Bearer `{{token}}`. Si hay datos, se espera `200`, `codigo: 0` y `origen` igual a `REDIS` o `POSTGRESQL`. Si ambos están vacíos, el servicio intenta obtenerlos en vivo.
- Casos de error: sin JWT → `401`; si el fallback externo falla, `502` o `503` según la causa.
- Para probar los orígenes, ejecuta antes `GET /productos` y vuelve a consultar los almacenados.

## 9. Pruebas transversales de errores

Ejecuta estas solicitudes contra un endpoint protegido, por ejemplo `GET /clientes`:

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

## 10. Orden recomendado de ejecución

1. `POST /clientes`, conservar IDs/cuenta y contraseña en el environment local.
2. `POST /auth/login`, guardar `accessToken` en `token`.
3. Validar búsquedas, consultas de cliente/cuenta/usuario y lista de cuentas activas.
4. Probar `PUT /clientes/{{clienteId}}`, `PUT /usuarios/{{usuarioId}}/password` y los endpoints de personas/productos.
5. Probar casos de error sin modificar datos válidos; usa entradas mal formadas independientes.
6. Ejecutar `DELETE /clientes/{{clienteId}}` y `DELETE /usuarios/{{usuarioId}}` al final y comprobar la revocación lógica.
7. Elimina el environment de pruebas o limpia las variables de token/contraseña al terminar. No ejecutes estas bajas contra IDs de producción.
