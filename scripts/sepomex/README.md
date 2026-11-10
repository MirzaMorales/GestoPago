# Catálogo de códigos postales (SEPOMEX)

El registro de clientes elige estado, municipio, colonia y código postal de listas, y el backend
rechaza domicilios que no existan en el catálogo (`POST /clientes`, `PUT /clientes`).

**Los datos no están en el repositorio.** Correos de México entrega el catálogo gratis para uso
particular y prohíbe distribuirlo a terceros; el repositorio es público. Cada ambiente lo carga una vez.

## Cargar el catálogo

1. Descarga el catálogo en
   https://www.correosdemexico.gob.mx/SSLServicios/ConsultaCP/CodigoPostal_Exportar.aspx
   (estado: *Todos*, formato: *Txt*). Obtienes `CPdescargatxt.zip`.
2. Conviértelo a CSV (UTF-8, sin duplicados):
   ```bash
   python -I convertir_sepomex.py CPdescargatxt.zip sepomex.csv
   ```
3. Arranca la app al menos una vez contra esa base (crea la tabla con la migración V11).
4. Carga el CSV desde esta carpeta (PowerShell, base Neon):
   ```powershell
   $env:PGHOST='<host>'; $env:PGDATABASE='neondb'; $env:PGUSER='<usuario>'; $env:PGSSLMODE='require'
   & "C:\Program Files\PostgreSQL\18\bin\psql.exe" -f cargar_sepomex.sql
   ```

Mientras la tabla esté vacía, el registro funciona sin validar el domicilio (y el backend lo avisa en el log).

## Endpoints (públicos, solo lectura)

| Método | Ruta | Respuesta (`datos`) |
|---|---|---|
| GET | `/catalogos/codigos-postales/{cp}` | Colonias del CP con municipio y estado |
| GET | `/catalogos/estados` | Lista de estados |
| GET | `/catalogos/municipios?estado=` | Municipios del estado |
| GET | `/catalogos/colonias?estado=&municipio=` | Colonias del municipio con su CP |
