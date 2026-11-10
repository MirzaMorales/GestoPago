-- Carga (o recarga) el catálogo de códigos postales en la base indicada por las variables PG* de psql.
-- Requiere que la app ya haya corrido la migración V11 (tabla cat_codigos_postales).
-- Uso (desde esta carpeta, con sepomex.csv generado por convertir_sepomex.py):
--   psql -f cargar_sepomex.sql
\set ON_ERROR_STOP on
BEGIN;
TRUNCATE cat_codigos_postales RESTART IDENTITY;
\copy cat_codigos_postales (codigo_postal, asentamiento, tipo_asentamiento, municipio, estado, ciudad, zona) FROM 'sepomex.csv' WITH (FORMAT csv, HEADER, ENCODING 'UTF8')
COMMIT;
ANALYZE cat_codigos_postales;
SELECT COUNT(*) AS asentamientos, COUNT(DISTINCT estado) AS estados, COUNT(DISTINCT codigo_postal) AS codigos_postales
FROM cat_codigos_postales;
