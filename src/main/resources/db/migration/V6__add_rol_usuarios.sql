-- Roles de acceso: ADMIN (ejecutivo, sin cliente asociado) y CLIENTE
ALTER TABLE usuarios ADD COLUMN rol VARCHAR(10) NOT NULL DEFAULT 'CLIENTE';
ALTER TABLE usuarios ALTER COLUMN cliente_id DROP NOT NULL;
ALTER TABLE usuarios ADD CONSTRAINT chk_usuarios_rol CHECK (rol IN ('ADMIN', 'CLIENTE'));
ALTER TABLE usuarios ADD CONSTRAINT chk_usuarios_cliente_rol CHECK (rol = 'ADMIN' OR cliente_id IS NOT NULL);
