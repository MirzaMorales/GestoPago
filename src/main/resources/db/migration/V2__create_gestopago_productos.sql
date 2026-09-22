CREATE TABLE IF NOT EXISTS gestopago_productos (
    id_producto           INTEGER PRIMARY KEY,
    producto              VARCHAR(256) NOT NULL,
    id_servicio           INTEGER NOT NULL,
    servicio              VARCHAR(256) NOT NULL,
    id_cat_tipo_servicio  INTEGER,
    tipo_front            INTEGER,
    legend                TEXT,
    fecha_actualizacion   TIMESTAMP NOT NULL DEFAULT NOW()
);
