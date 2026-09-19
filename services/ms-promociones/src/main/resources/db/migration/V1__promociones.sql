-- ms-promociones: se evalua en cada linea que el cajero agrega. Despues del catalogo,
-- es el endpoint mas llamado del sistema, y es CPU pura sobre reglas.
-- Se separo por aislamiento de CPU y porque se degrada sin bloquear la venta: si no
-- responde en 300 ms, la linea entra sin promocion y la venta continua.
-- Es el corte mas discutible de los nueve. Se mide en las pruebas de carga del paso 12:
-- si el p99 desde ventas no baja de 20 ms, vuelve a ser una libreria dentro de ventas.
-- Origen: V3__promociones.sql del monolito.
-- producto_id no lleva REFERENCES: productos vive en otra base.

CREATE TABLE promociones (
    id               uuid          PRIMARY KEY,
    nombre           varchar(150)  NOT NULL,
    descripcion      varchar(500),
    tipo_beneficio   varchar(30)   NOT NULL,
    valor_beneficio  numeric(12,2) NOT NULL,
    requiere_cliente boolean       NOT NULL DEFAULT false,
    cantidad_minima  integer       NOT NULL DEFAULT 1,
    vigencia_inicio  date,
    vigencia_fin     date,
    activa           boolean       NOT NULL DEFAULT true,
    version          bigint        NOT NULL DEFAULT 0
);

-- Indice que sirve a la unica consulta caliente: promociones vigentes hoy.
CREATE INDEX idx_promociones_vigentes ON promociones (vigencia_inicio, vigencia_fin)
    WHERE activa = true;

CREATE TABLE promocion_condiciones (
    id           uuid PRIMARY KEY,
    promocion_id uuid NOT NULL REFERENCES promociones (id) ON DELETE CASCADE,
    producto_id  uuid NOT NULL
);
CREATE INDEX idx_promocion_condiciones_promocion ON promocion_condiciones (promocion_id);
CREATE INDEX idx_promocion_condiciones_producto  ON promocion_condiciones (producto_id);
