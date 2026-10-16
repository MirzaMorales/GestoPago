# PR: `dev` → `main`

**Título sugerido:** `release: API de administración de usuarios con endpoints por body, errores centralizados y roles`

## 1. Resumen del release

Este PR lleva a `main` el contenido ya integrado en `dev` (PR `fix/resolucion-pruebas` → `dev`). Es un **release con cambios incompatibles en la API**: las rutas con parámetros en la URL desaparecen y todas las respuestas cambian de estructura.

Qué entrega:

- API REST por capas (Controller / Service / Repository / DTO / Entity) para clientes, cuentas y usuarios, cumpliendo la especificación del profesor.
- **Sin `@PathVariable` ni `@RequestParam`**: identificadores y criterios viajan en `@RequestBody` validado con `@Valid`.
- **Un único endpoint de consulta** por recurso con filtros combinables (`/clientes/buscar`, `/cuentas/buscar`).
- **Respuesta uniforme** (`timestamp`, `status`, `mensaje`, `datos` | `detalles`) para éxitos y errores.
- **Manejo global de errores** con `@RestControllerAdvice` (`ValidadorCampos` + `GlobalExceptionHandler`) y excepciones de negocio propias; sin trazas ni SQL hacia el cliente.
- **Roles `ADMIN` / `CLIENTE`** con reglas de propiedad (un cliente solo ve y modifica lo suyo).
- **Módulo Personas eliminado** (código y tabla).
- Documentación de pruebas e integración actualizada.

Detalle técnico completo y tabla de verificación: ver `PR_fix-resolucion-pruebas_a_dev.md`.

## 2. Mapa de rutas finales

| Método | Ruta | Acceso |
|---|---|---|
| POST | `/auth/login` | Público |
| POST | `/clientes` | Público (alta) |
| POST | `/clientes/buscar` | ADMIN o dueño (por `id`) |
| PUT | `/clientes` | ADMIN o dueño |
| DELETE | `/clientes` | ADMIN |
| POST | `/cuentas/buscar` | ADMIN, o dueño con `numeroCuenta` |
| POST | `/cuentas/saldo` | ADMIN o dueño |
| POST | `/usuarios/consultar` | ADMIN o el propio usuario |
| PUT | `/usuarios/password` | ADMIN o el propio usuario |
| DELETE | `/usuarios` | ADMIN |
| GET | `/productos`, `/getProductList`, `/productos/almacenados` | Autenticado |

## 3. Impacto en despliegue (leer antes de mergear)

1. **Migraciones Flyway nuevas**
   - `V6__add_rol_usuarios.sql`: agrega la columna `rol` a `usuarios`.
   - `V7__drop_personas.sql`: **elimina la tabla `personas` y sus datos**. Respaldar antes si hay información que conservar.
2. **Variables de entorno**
   - `APP_JWT_SECRET` (obligatoria; sin valor por defecto).
   - `APP_ADMIN_CORREO` y `APP_ADMIN_PASSWORD`: si se definen, se crea el ADMIN inicial al arrancar. Sin ellas no habrá ningún usuario con permisos de administración.
   - Base de datos (`DB_URL`, `DB_USER`, `DB_PASSWORD`) y Redis (`SPRING_DATA_REDIS_URL` en Render) sin cambios respecto a `main`/`dev`.
3. **Consumidores de la API** (Postman, front, integraciones) deben migrar a las rutas de la sección 2 y leer los datos desde `datos`.
4. Despliegue en Render con `Dockerfile` y `render.yaml` ya presentes en la rama; no hay cambios en ellos en este release.

## 4. Plan de pruebas previo al merge

- [ ] CI/`./gradlew test` en verde sobre `dev`.
- [ ] Arrancar contra una base limpia y confirmar que Flyway aplica V1–V7 sin error y que se crea el ADMIN inicial.
- [ ] Ejecutar la colección de `documentacion/pruebas_endpoints_postman.md` (login admin → registro → búsqueda → actualización → cuentas/saldo → usuarios → bajas).
- [ ] Verificar los códigos clave: 400 (validación), 401 (sin token), 403 (recurso ajeno / rol insuficiente), 404 (inexistente), 409 (duplicado), 405 (método) y que `GET /clientes/{id}` ya responde 404.
- [ ] Confirmar que ningún error 500 expone trazas, SQL ni nombres internos.
- [ ] En el entorno ya desplegado: respaldar BD antes de aplicar `V7`.

Resultado de la verificación manual hecha sobre la rama: 36 solicitudes con el código y cuerpo esperados (ver tabla en el PR a `dev`).

## 5. Plan de reversa

- El código se puede revertir con `git revert` del merge commit.
- `V7` no es reversible automáticamente (borra datos de `personas`): la reversa requiere restaurar el respaldo. `V6` solo agrega una columna y es inocua si se revierte el código.

## 6. Pendientes conocidos (no bloquean el release)

- Regenerar las evidencias (`Evidencias_Pruebas_Endpoints_GestoPago.docx`, capturas E05–E08), que describen los endpoints anteriores.
- `POST /clientes` devuelve `cuentas: []` y `usuario: null` en su respuesta aunque ambos se crean; se ven al consultar con `/clientes/buscar`.
- Una búsqueda de ADMIN sin `id` y sin coincidencias devuelve todos los clientes (comportamiento heredado), en lugar de una lista vacía.

## 7. Checklist de revisión

- [ ] Cumple todas las restricciones obligatorias de la especificación (sin `@PathVariable`, sin `@RequestParam`, criterios en `@RequestBody`, `@Valid`, `@RestControllerAdvice`, sin `try-catch` en controladores, sin entidades JPA en la API).
- [ ] Variables de entorno configuradas en el entorno destino.
- [ ] Respaldo de BD realizado.
- [ ] Equipo/consumidores informados del cambio de rutas y de formato de respuesta.
