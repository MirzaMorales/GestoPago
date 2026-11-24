-- Script de migración Flyway V13: catálogo de ocupaciones para el registro y la actualización de clientes.
-- Agrupado según las divisiones del Sistema Nacional de Clasificación de Ocupaciones (SINCO 2019, INEGI),
-- con ocupaciones frecuentes de cada división. El cliente elige de esta lista en lugar de escribir texto libre.
CREATE TABLE IF NOT EXISTS cat_ocupaciones (
    id      SERIAL PRIMARY KEY,
    nombre  VARCHAR(100) NOT NULL UNIQUE,
    grupo   VARCHAR(120) NOT NULL,
    orden   INTEGER      NOT NULL
);

INSERT INTO cat_ocupaciones (nombre, grupo, orden) VALUES
-- 1. Funcionarios, directores y jefes
('Director(a) o gerente general', 'Funcionarios, directores y jefes', 1),
('Gerente de área o departamento', 'Funcionarios, directores y jefes', 1),
('Jefe(a) o coordinador(a) de área', 'Funcionarios, directores y jefes', 1),
('Funcionario(a) público(a)', 'Funcionarios, directores y jefes', 1),
('Empresario(a) o dueño(a) de negocio', 'Funcionarios, directores y jefes', 1),
-- 2. Profesionistas y técnicos
('Abogado(a)', 'Profesionistas y técnicos', 2),
('Arquitecto(a)', 'Profesionistas y técnicos', 2),
('Contador(a) público(a)', 'Profesionistas y técnicos', 2),
('Diseñador(a) gráfico(a)', 'Profesionistas y técnicos', 2),
('Docente o profesor(a)', 'Profesionistas y técnicos', 2),
('Enfermero(a)', 'Profesionistas y técnicos', 2),
('Ingeniero(a) civil', 'Profesionistas y técnicos', 2),
('Ingeniero(a) en sistemas o software', 'Profesionistas y técnicos', 2),
('Ingeniero(a) industrial', 'Profesionistas y técnicos', 2),
('Ingeniero(a) mecánico(a) o eléctrico(a)', 'Profesionistas y técnicos', 2),
('Médico(a)', 'Profesionistas y técnicos', 2),
('Odontólogo(a)', 'Profesionistas y técnicos', 2),
('Psicólogo(a)', 'Profesionistas y técnicos', 2),
('Químico(a) o farmacéutico(a)', 'Profesionistas y técnicos', 2),
('Técnico(a) en informática', 'Profesionistas y técnicos', 2),
('Técnico(a) en electrónica', 'Profesionistas y técnicos', 2),
('Técnico(a) en salud', 'Profesionistas y técnicos', 2),
('Veterinario(a)', 'Profesionistas y técnicos', 2),
('Periodista o comunicador(a)', 'Profesionistas y técnicos', 2),
-- 3. Trabajadores auxiliares en actividades administrativas
('Asistente administrativo(a)', 'Trabajadores auxiliares en actividades administrativas', 3),
('Auxiliar contable', 'Trabajadores auxiliares en actividades administrativas', 3),
('Capturista de datos', 'Trabajadores auxiliares en actividades administrativas', 3),
('Cajero(a) bancario(a)', 'Trabajadores auxiliares en actividades administrativas', 3),
('Recepcionista', 'Trabajadores auxiliares en actividades administrativas', 3),
('Secretario(a)', 'Trabajadores auxiliares en actividades administrativas', 3),
('Agente de atención a clientes', 'Trabajadores auxiliares en actividades administrativas', 3),
-- 4. Comerciantes, empleados en ventas y agentes de ventas
('Comerciante', 'Comerciantes, empleados en ventas y agentes de ventas', 4),
('Vendedor(a) en tienda o almacén', 'Comerciantes, empleados en ventas y agentes de ventas', 4),
('Cajero(a) de comercio', 'Comerciantes, empleados en ventas y agentes de ventas', 4),
('Agente de ventas', 'Comerciantes, empleados en ventas y agentes de ventas', 4),
('Vendedor(a) ambulante', 'Comerciantes, empleados en ventas y agentes de ventas', 4),
-- 5. Trabajadores en servicios personales y vigilancia
('Cocinero(a)', 'Trabajadores en servicios personales y vigilancia', 5),
('Mesero(a)', 'Trabajadores en servicios personales y vigilancia', 5),
('Estilista o peluquero(a)', 'Trabajadores en servicios personales y vigilancia', 5),
('Guardia de seguridad', 'Trabajadores en servicios personales y vigilancia', 5),
('Policía', 'Trabajadores en servicios personales y vigilancia', 5),
('Militar', 'Trabajadores en servicios personales y vigilancia', 5),
('Trabajador(a) del hogar', 'Trabajadores en servicios personales y vigilancia', 5),
('Cuidador(a) de personas', 'Trabajadores en servicios personales y vigilancia', 5),
-- 6. Trabajadores en actividades agrícolas, ganaderas, forestales, caza y pesca
('Agricultor(a)', 'Trabajadores en actividades agrícolas, ganaderas, forestales, caza y pesca', 6),
('Ganadero(a)', 'Trabajadores en actividades agrícolas, ganaderas, forestales, caza y pesca', 6),
('Pescador(a)', 'Trabajadores en actividades agrícolas, ganaderas, forestales, caza y pesca', 6),
('Jornalero(a) agrícola', 'Trabajadores en actividades agrícolas, ganaderas, forestales, caza y pesca', 6),
-- 7. Trabajadores artesanales, en la construcción y otros oficios
('Albañil', 'Trabajadores artesanales, en la construcción y otros oficios', 7),
('Artesano(a)', 'Trabajadores artesanales, en la construcción y otros oficios', 7),
('Carpintero(a)', 'Trabajadores artesanales, en la construcción y otros oficios', 7),
('Electricista', 'Trabajadores artesanales, en la construcción y otros oficios', 7),
('Herrero(a) o soldador(a)', 'Trabajadores artesanales, en la construcción y otros oficios', 7),
('Mecánico(a) automotriz', 'Trabajadores artesanales, en la construcción y otros oficios', 7),
('Panadero(a)', 'Trabajadores artesanales, en la construcción y otros oficios', 7),
('Pintor(a)', 'Trabajadores artesanales, en la construcción y otros oficios', 7),
('Plomero(a)', 'Trabajadores artesanales, en la construcción y otros oficios', 7),
('Costurero(a) o sastre', 'Trabajadores artesanales, en la construcción y otros oficios', 7),
-- 8. Operadores de maquinaria industrial, ensambladores, choferes y conductores de transporte
('Chofer o conductor(a)', 'Operadores de maquinaria, ensambladores y conductores de transporte', 8),
('Operador(a) de maquinaria industrial', 'Operadores de maquinaria, ensambladores y conductores de transporte', 8),
('Ensamblador(a)', 'Operadores de maquinaria, ensambladores y conductores de transporte', 8),
('Repartidor(a)', 'Operadores de maquinaria, ensambladores y conductores de transporte', 8),
('Taxista o conductor(a) de plataforma', 'Operadores de maquinaria, ensambladores y conductores de transporte', 8),
-- 9. Trabajadores en actividades elementales y de apoyo
('Ayudante general', 'Trabajadores en actividades elementales y de apoyo', 9),
('Cargador(a) o estibador(a)', 'Trabajadores en actividades elementales y de apoyo', 9),
('Personal de limpieza', 'Trabajadores en actividades elementales y de apoyo', 9),
('Empacador(a)', 'Trabajadores en actividades elementales y de apoyo', 9),
('Jardinero(a)', 'Trabajadores en actividades elementales y de apoyo', 9),
-- Sin ocupación remunerada
('Estudiante', 'Sin ocupación remunerada', 10),
('Labores del hogar', 'Sin ocupación remunerada', 10),
('Jubilado(a) o pensionado(a)', 'Sin ocupación remunerada', 10),
('Buscando empleo', 'Sin ocupación remunerada', 10),
-- Otra
('Otra ocupación', 'Otra', 11)
ON CONFLICT (nombre) DO NOTHING;
