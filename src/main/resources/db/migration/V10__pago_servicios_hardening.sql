-- Script de migración Flyway V10: endurecimiento del pago de servicios.
--  * Catálogo: precio publicado por GestoPago (tipoFront 1 = precio final; tipoFront 2 = comisión).
--  * Cuentas: control de intentos fallidos del PIN (bloqueo temporal).
--  * Transacciones: idempotencia, UPC, payload enviado (para confirmTx) y fechas de envío/resolución.
--  * Bitácora de intercambios con GestoPago (requisito del proveedor: guardar requests y responses).

ALTER TABLE gestopago_productos ADD COLUMN IF NOT EXISTS precio NUMERIC(15, 2);

ALTER TABLE cuentas ADD COLUMN IF NOT EXISTS pin_intentos_fallidos INTEGER NOT NULL DEFAULT 0;
ALTER TABLE cuentas ADD COLUMN IF NOT EXISTS pin_bloqueado_hasta TIMESTAMP WITH TIME ZONE;

ALTER TABLE transacciones_pago ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(64);
ALTER TABLE transacciones_pago ADD COLUMN IF NOT EXISTS huella_solicitud VARCHAR(64);
ALTER TABLE transacciones_pago ADD COLUMN IF NOT EXISTS upc VARCHAR(15);
ALTER TABLE transacciones_pago ADD COLUMN IF NOT EXISTS payload_enviado TEXT;
ALTER TABLE transacciones_pago ADD COLUMN IF NOT EXISTS fecha_envio TIMESTAMP WITH TIME ZONE;
ALTER TABLE transacciones_pago ADD COLUMN IF NOT EXISTS fecha_resolucion TIMESTAMP WITH TIME ZONE;

-- Una llave de idempotencia solo puede usarse una vez por cuenta; el UPC es único globalmente.
CREATE UNIQUE INDEX IF NOT EXISTS uq_transacciones_pago_cuenta_idempotency
    ON transacciones_pago(cuenta_id, idempotency_key);
CREATE UNIQUE INDEX IF NOT EXISTS uq_transacciones_pago_upc ON transacciones_pago(upc);
CREATE INDEX IF NOT EXISTS idx_transacciones_pago_fecha_creacion ON transacciones_pago(fecha_creacion);

CREATE TABLE IF NOT EXISTS gestopago_tx_log (
    id BIGSERIAL PRIMARY KEY,
    transaccion_id BIGINT NOT NULL REFERENCES transacciones_pago(id) ON DELETE RESTRICT,
    operacion VARCHAR(20) NOT NULL CHECK (operacion IN ('SEND_TX', 'CONFIRM_TX')),
    payload_enviado TEXT,
    respuesta TEXT,
    resultado VARCHAR(40) NOT NULL,
    fecha TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_gestopago_tx_log_transaccion ON gestopago_tx_log(transaccion_id);
