-- Tabla heredada usada por /personas, /personasActualiza y /personasElimina.
CREATE TABLE IF NOT EXISTS personas (
    id               SERIAL PRIMARY KEY,
    nombre           VARCHAR(100) NOT NULL,
    apellido_paterno VARCHAR(100) NOT NULL,
    apellido_materno VARCHAR(100) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_personas_nombre ON personas (nombre);
