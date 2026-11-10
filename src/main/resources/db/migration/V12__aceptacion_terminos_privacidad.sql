-- Script de migración Flyway V12: constancia del consentimiento del cliente.
-- Se guarda qué versión de los Términos y Condiciones y del Aviso de Privacidad aceptó y cuándo.
-- Los clientes registrados antes de esta versión quedan con NULL (no aceptaron por la app).
ALTER TABLE clientes ADD COLUMN IF NOT EXISTS terminos_version VARCHAR(20);
ALTER TABLE clientes ADD COLUMN IF NOT EXISTS terminos_aceptados_en TIMESTAMP WITH TIME ZONE;
