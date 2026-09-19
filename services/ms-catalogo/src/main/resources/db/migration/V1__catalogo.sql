-- ms-catalogo: lectura masiva sobre datos casi estaticos. 2 replicas de lectura y
-- cache de dos niveles (Caffeine en el pod, Redis compartido). Cero contencion de
-- escritura, por eso se separo de inventario: perfiles de carga opuestos.
-- Origen: mitad "catalogo" de V2__catalogo_inventario.sql del monolito.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE categorias (
    id     uuid PRIMARY KEY,
    nombre varchar(150) NOT NULL
);

CREATE TABLE laboratorios (
    id     uuid PRIMARY KEY,
    nombre varchar(150) NOT NULL
);

CREATE TABLE presentaciones (
    id            uuid PRIMARY KEY,
    nombre        varchar(100) NOT NULL,
    unidad_medida varchar(50)  NOT NULL
);

CREATE TABLE productos (
    id                    uuid PRIMARY KEY,
    codigo_interno        varchar(50)   NOT NULL UNIQUE,
    codigo_barras         varchar(50),
    nombre_comercial      varchar(200)  NOT NULL,
    descripcion           varchar(1000) NOT NULL DEFAULT '',
    tipo_producto         varchar(30)   NOT NULL,
    categoria_id          uuid          NOT NULL REFERENCES categorias (id),
    laboratorio_id        uuid          NOT NULL REFERENCES laboratorios (id),
    presentacion_id       uuid          NOT NULL REFERENCES presentaciones (id),
    precio_venta          numeric(12,2) NOT NULL,
    es_controlado         boolean       NOT NULL DEFAULT false,
    requiere_receta       boolean       NOT NULL DEFAULT false,
    tipo_receta_requerida varchar(30),
    estado                varchar(20)   NOT NULL DEFAULT 'ACTIVO',
    version               bigint        NOT NULL DEFAULT 0
);
CREATE INDEX idx_productos_categoria     ON productos (categoria_id);
CREATE INDEX idx_productos_laboratorio   ON productos (laboratorio_id);
CREATE INDEX idx_productos_codigo_barras ON productos (codigo_barras) WHERE codigo_barras IS NOT NULL;
-- Busqueda por nombre desde la caja: trigram. Un LIKE '%texto%' sin indice a 5000 req/s
-- es un escaneo secuencial por cada tecla que pulsa el cajero.
CREATE INDEX idx_productos_nombre_trgm ON productos USING gin (nombre_comercial gin_trgm_ops);
