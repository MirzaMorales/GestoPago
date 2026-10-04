-- Script de migración Flyway V3: Tablas para el Módulo de Onboarding de Clientes Personas Físicas

CREATE TABLE IF NOT EXISTS domicilios (
    id BIGSERIAL PRIMARY KEY,
    calle VARCHAR(100) NOT NULL,
    numero_exterior VARCHAR(20) NOT NULL,
    numero_interior VARCHAR(20),
    colonia VARCHAR(100) NOT NULL,
    municipio VARCHAR(100) NOT NULL,
    estado VARCHAR(100) NOT NULL,
    codigo_postal VARCHAR(5) NOT NULL,
    pais VARCHAR(50) NOT NULL DEFAULT 'México'
);

CREATE TABLE IF NOT EXISTS clientes (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL,
    segundo_nombre VARCHAR(50),
    apellido_paterno VARCHAR(50) NOT NULL,
    apellido_materno VARCHAR(50) NOT NULL,
    fecha_nacimiento DATE NOT NULL,
    curp VARCHAR(18) NOT NULL UNIQUE,
    rfc VARCHAR(13) NOT NULL UNIQUE,
    sexo VARCHAR(20) NOT NULL,
    nacionalidad VARCHAR(50) NOT NULL DEFAULT 'Mexicana',
    estado_civil VARCHAR(30) NOT NULL,
    correo VARCHAR(100) NOT NULL UNIQUE,
    telefono_movil VARCHAR(10) NOT NULL,
    telefono_alternativo VARCHAR(10),
    ocupacion VARCHAR(100) NOT NULL,
    empresa VARCHAR(100) NOT NULL,
    ingreso_mensual NUMERIC(15, 2) NOT NULL CHECK (ingreso_mensual > 0),
    domicilio_id BIGINT NOT NULL REFERENCES domicilios(id) ON DELETE CASCADE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cuentas (
    id BIGSERIAL PRIMARY KEY,
    numero_cuenta VARCHAR(16) NOT NULL UNIQUE,
    cliente_id BIGINT NOT NULL REFERENCES clientes(id) ON DELETE RESTRICT,
    saldo NUMERIC(15, 2) NOT NULL DEFAULT 0.00 CHECK (saldo >= 0),
    estatus VARCHAR(20) NOT NULL DEFAULT 'ACTIVA' CHECK (estatus IN ('ACTIVA', 'INACTIVA', 'BLOQUEADA', 'CANCELADA')),
    fecha_apertura TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS usuarios (
    id BIGSERIAL PRIMARY KEY,
    cliente_id BIGINT NOT NULL UNIQUE REFERENCES clientes(id) ON DELETE CASCADE,
    correo VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Índices optimizados para acelerar búsquedas dinámicas multicriterio
CREATE INDEX IF NOT EXISTS idx_clientes_curp ON clientes(curp);
CREATE INDEX IF NOT EXISTS idx_clientes_rfc ON clientes(rfc);
CREATE INDEX IF NOT EXISTS idx_clientes_correo ON clientes(correo);
CREATE INDEX IF NOT EXISTS idx_clientes_activo ON clientes(activo);
CREATE INDEX IF NOT EXISTS idx_clientes_fecha_creacion ON clientes(fecha_creacion);

CREATE INDEX IF NOT EXISTS idx_cuentas_numero_cuenta ON cuentas(numero_cuenta);
CREATE INDEX IF NOT EXISTS idx_cuentas_cliente_id ON cuentas(cliente_id);
CREATE INDEX IF NOT EXISTS idx_cuentas_estatus ON cuentas(estatus);

CREATE INDEX IF NOT EXISTS idx_usuarios_correo ON usuarios(correo);
CREATE INDEX IF NOT EXISTS idx_usuarios_activo ON usuarios(activo);
