ALTER TABLE clientes
    ADD CONSTRAINT uq_clientes_domicilio_id UNIQUE (domicilio_id);

ALTER TABLE clientes
    DROP CONSTRAINT clientes_domicilio_id_fkey,
    ADD CONSTRAINT fk_clientes_domicilio
        FOREIGN KEY (domicilio_id) REFERENCES domicilios(id) ON DELETE RESTRICT;

ALTER TABLE clientes
    ADD CONSTRAINT ck_clientes_telefono_movil
        CHECK (telefono_movil ~ '^[0-9]{10}$'),
    ADD CONSTRAINT ck_clientes_telefono_alternativo
        CHECK (telefono_alternativo IS NULL OR telefono_alternativo ~ '^[0-9]{10}$');

ALTER TABLE domicilios
    ADD CONSTRAINT ck_domicilios_codigo_postal
        CHECK (codigo_postal ~ '^[0-9]{5}$');

DROP INDEX IF EXISTS idx_clientes_curp;
DROP INDEX IF EXISTS idx_clientes_rfc;
DROP INDEX IF EXISTS idx_clientes_correo;
DROP INDEX IF EXISTS idx_cuentas_numero_cuenta;
DROP INDEX IF EXISTS idx_usuarios_correo;
