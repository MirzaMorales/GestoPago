-- Script de migración Flyway V8: PIN de autorización (hash BCrypt) para operar pagos desde la cuenta.
-- Es NULL hasta que el titular lo configure.
ALTER TABLE cuentas ADD COLUMN IF NOT EXISTS pin_autorizacion VARCHAR(100);
