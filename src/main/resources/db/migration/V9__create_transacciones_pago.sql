-- Script de migración Flyway V9: Bitácora de transacciones de pago de servicios.
-- El monto se retiene (PENDIENTE) antes de llamar a GestoPago y luego se confirma (EXITOSA),
-- se devuelve (FALLIDA) o queda EN_REVISION cuando no se sabe si GestoPago lo procesó.

CREATE TABLE IF NOT EXISTS transacciones_pago (
    id BIGSERIAL PRIMARY KEY,
    cuenta_id BIGINT NOT NULL REFERENCES cuentas(id) ON DELETE RESTRICT,
    id_producto INTEGER NOT NULL,
    id_servicio INTEGER NOT NULL,
    telefono_referencia VARCHAR(100) NOT NULL,
    monto NUMERIC(15, 2) NOT NULL CHECK (monto > 0),
    estatus VARCHAR(20) NOT NULL CHECK (estatus IN ('PENDIENTE', 'EXITOSA', 'FALLIDA', 'EN_REVISION')),
    gp_id_tx VARCHAR(50),
    gp_num_autorizacion VARCHAR(60),
    gp_comision NUMERIC(15, 2),
    detalle VARCHAR(500),
    fecha_creacion TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_transacciones_pago_cuenta_id ON transacciones_pago(cuenta_id);
CREATE INDEX IF NOT EXISTS idx_transacciones_pago_estatus ON transacciones_pago(estatus);
