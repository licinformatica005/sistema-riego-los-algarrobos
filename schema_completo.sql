-- ============================================
-- Sistema de Monitoreo y Gestión de Riego
-- Modelo relacional - Los Algarrobos
-- ============================================

CREATE TABLE controladores (
    controlador_id INT PRIMARY KEY,
    identificador VARCHAR(50),
    volumen_agua_total DECIMAL(10,2)
);

CREATE TABLE sectores (
    sector_id INT PRIMARY KEY,
    nombre VARCHAR(100),
    superficie DECIMAL(10,2),
    tipo_cultivo VARCHAR(100),
    controlador_id INT,
    FOREIGN KEY (controlador_id) REFERENCES controladores(controlador_id)
);

CREATE TABLE valvulas (
    valvula_id INT PRIMARY KEY,
    sector_id INT UNIQUE,
    caudal DECIMAL(10,2),
    estado VARCHAR(20),
    FOREIGN KEY (sector_id) REFERENCES sectores(sector_id)
);

CREATE TABLE sensores (
    sensor_id INT PRIMARY KEY,
    valvula_id INT,
    tipo VARCHAR(50),
    FOREIGN KEY (valvula_id) REFERENCES valvulas(valvula_id)
);

CREATE TABLE umbrales (
    umbral_id INT PRIMARY KEY,
    sector_id INT UNIQUE,
    humedad_minima DECIMAL(5,2),
    humedad_maxima DECIMAL(5,2),
    FOREIGN KEY (sector_id) REFERENCES sectores(sector_id)
);

CREATE TABLE registros_humedad (
    registro_id INT PRIMARY KEY,
    sensor_id INT,
    fecha_registro DATETIME,
    humedad_porcentaje DECIMAL(5,2),
    FOREIGN KEY (sensor_id) REFERENCES sensores(sensor_id)
);

CREATE TABLE registros_clima (
    registro_id INT PRIMARY KEY,
    sensor_id INT,
    fecha_registro DATETIME,
    temperatura DECIMAL(5,2),
    precipitacion_mm DECIMAL(6,2),
    FOREIGN KEY (sensor_id) REFERENCES sensores(sensor_id)
);

CREATE TABLE registros_riego (
    registro_id INT PRIMARY KEY,
    valvula_id INT,
    fecha_inicio DATETIME,
    fecha_fin DATETIME,
    volumen_litros DECIMAL(10,2),
    tipo_activacion VARCHAR(20),
    FOREIGN KEY (valvula_id) REFERENCES valvulas(valvula_id)
);

CREATE TABLE alertas (
    alerta_id INT PRIMARY KEY,
    sector_id INT,
    fecha_alerta DATETIME,
    tipo_alerta VARCHAR(50),
    descripcion VARCHAR(255),
    atendida BOOLEAN,
    FOREIGN KEY (sector_id) REFERENCES sectores(sector_id)
);

-- ============================================
-- Datos de prueba
-- ============================================

INSERT INTO controladores (controlador_id, identificador, volumen_agua_total)
VALUES
    (1, 'CTRL-NORTE', 0.0);

INSERT INTO sectores (sector_id, nombre, superficie, tipo_cultivo, controlador_id)
VALUES
    (1, 'Sector Viñedo A', 4.5, 'Vid', 1),
    (2, 'Sector Olivar B', 6.2, 'Olivo', 1),
    (3, 'Sector Hortalizas C', 2.1, 'Hortalizas', 1);

INSERT INTO valvulas (valvula_id, sector_id, caudal, estado)
VALUES
    (1, 1, 12.5, 'Activa'),
    (2, 2, 10.0, 'Inactiva'),
    (3, 3, 8.0, 'Inactiva');

INSERT INTO sensores (sensor_id, valvula_id, tipo)
VALUES
    (1, 1, 'Humedad de suelo'),
    (2, 1, 'Clima'),
    (3, 2, 'Humedad de suelo'),
    (4, 3, 'Humedad de suelo');

INSERT INTO umbrales (umbral_id, sector_id, humedad_minima, humedad_maxima)
VALUES
    (1, 1, 30.00, 60.00),
    (2, 2, 25.00, 55.00),
    (3, 3, 35.00, 65.00);

INSERT INTO registros_humedad (registro_id, sensor_id, fecha_registro, humedad_porcentaje)
VALUES
    (1, 1, '2026-09-20 08:00:00', 28.50),
    (2, 1, '2026-09-20 10:00:00', 45.20),
    (3, 3, '2026-09-20 08:15:00', 22.10);

INSERT INTO registros_clima (registro_id, sensor_id, fecha_registro, temperatura, precipitacion_mm)
VALUES
    (1, 2, '2026-09-20 08:00:00', 24.3, 0.0),
    (2, 2, '2026-09-20 14:00:00', 29.8, 0.0);

INSERT INTO registros_riego (registro_id, valvula_id, fecha_inicio, fecha_fin, volumen_litros, tipo_activacion)
VALUES
    (1, 1, '2026-09-20 08:00:00', '2026-09-20 08:45:00', 562.50, 'Automático'),
    (2, 2, '2026-09-19 07:00:00', '2026-09-19 07:30:00', 300.00, 'Manual');

INSERT INTO alertas (alerta_id, sector_id, fecha_alerta, tipo_alerta, descripcion, atendida)
VALUES
    (1, 2, '2026-09-20 08:15:00', 'Humedad crítica', 'Humedad por debajo del umbral mínimo configurado', FALSE);

-- ============================================
-- Consultas de referencia y borrado de prueba
-- ============================================

-- Consulta 1: estado actual de cada sector con su última lectura de humedad
SELECT
    S.nombre AS 'Sector',
    S.tipo_cultivo AS 'Cultivo',
    V.estado AS 'Estado Valvula',
    RH.humedad_porcentaje AS 'Ultima Humedad',
    RH.fecha_registro AS 'Fecha Registro'
FROM
    sectores S,
    valvulas V,
    sensores SE,
    registros_humedad RH
WHERE
    V.sector_id = S.sector_id
    AND SE.valvula_id = V.valvula_id
    AND SE.tipo = 'Humedad de suelo'
    AND RH.sensor_id = SE.sensor_id;

-- Consulta 2: consumo total de agua por sector
SELECT
    S.nombre AS 'Sector',
    SUM(RR.volumen_litros) AS 'Consumo Total Litros'
FROM
    sectores S,
    valvulas V,
    registros_riego RR
WHERE
    V.sector_id = S.sector_id
    AND RR.valvula_id = V.valvula_id
GROUP BY
    S.nombre;

-- Borrado de datos de prueba
DELETE FROM alertas;
DELETE FROM registros_riego;
DELETE FROM registros_clima;
DELETE FROM registros_humedad;
DELETE FROM umbrales;
DELETE FROM sensores;
DELETE FROM valvulas;
DELETE FROM sectores;
DELETE FROM controladores;
