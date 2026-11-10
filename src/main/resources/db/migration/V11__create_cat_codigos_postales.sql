-- Script de migración Flyway V11: catálogo nacional de códigos postales (SEPOMEX).
-- Permite que el registro elija estado, municipio y colonia de listas, y que el servidor valide el domicilio.
-- Los datos NO van en el repositorio (el catálogo de Correos de México prohíbe su distribución a terceros):
-- se cargan una vez con scripts/sepomex (ver README de esa carpeta).
CREATE TABLE IF NOT EXISTS cat_codigos_postales (
    id                 BIGSERIAL PRIMARY KEY,
    codigo_postal      VARCHAR(5)   NOT NULL CHECK (codigo_postal ~ '^[0-9]{5}$'),
    asentamiento       VARCHAR(150) NOT NULL,
    tipo_asentamiento  VARCHAR(60),
    municipio          VARCHAR(100) NOT NULL,
    estado             VARCHAR(100) NOT NULL,
    ciudad             VARCHAR(100),
    zona               VARCHAR(20)
);

CREATE INDEX IF NOT EXISTS idx_cat_cp_codigo_postal ON cat_codigos_postales (codigo_postal);
CREATE INDEX IF NOT EXISTS idx_cat_cp_estado_municipio ON cat_codigos_postales (estado, municipio);
