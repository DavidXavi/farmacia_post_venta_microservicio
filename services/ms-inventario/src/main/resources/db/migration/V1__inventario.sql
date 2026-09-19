-- ms-inventario: el servicio con contencion de escritura. Es el corazon del diseno
-- de alta carga y por eso se separo de catalogo: perfiles de carga opuestos.
-- Origen: mitad "inventario" de V2__catalogo_inventario.sql del monolito, mas las
-- tablas nuevas stock_local y reservas, que son las que resuelven la contencion.
-- producto_id ya NO lleva REFERENCES: la tabla productos vive en otra base.

-- ---------------------------------------------------------------------------
-- stock_local: el contador. Una fila por (producto, local).
-- Reservar es UN solo UPDATE condicional atomico, sin SELECT FOR UPDATE y sin
-- riesgo de deadlock. La contencion queda acotada al local, que es justamente el
-- shard natural del negocio: dos boticas distintas nunca se pelean la misma fila.
-- Particionado por hash de local_id para repartir la carga entre particiones.
-- ---------------------------------------------------------------------------
CREATE TABLE stock_local (
    producto_id    uuid        NOT NULL,
    local_id       uuid        NOT NULL,
    disponible     integer     NOT NULL DEFAULT 0 CHECK (disponible >= 0),
    reservado      integer     NOT NULL DEFAULT 0 CHECK (reservado  >= 0),
    actualizado_en timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (producto_id, local_id),
    CHECK (reservado <= disponible)
) PARTITION BY HASH (local_id);

CREATE TABLE stock_local_p0 PARTITION OF stock_local FOR VALUES WITH (MODULUS 8, REMAINDER 0);
CREATE TABLE stock_local_p1 PARTITION OF stock_local FOR VALUES WITH (MODULUS 8, REMAINDER 1);
CREATE TABLE stock_local_p2 PARTITION OF stock_local FOR VALUES WITH (MODULUS 8, REMAINDER 2);
CREATE TABLE stock_local_p3 PARTITION OF stock_local FOR VALUES WITH (MODULUS 8, REMAINDER 3);
CREATE TABLE stock_local_p4 PARTITION OF stock_local FOR VALUES WITH (MODULUS 8, REMAINDER 4);
CREATE TABLE stock_local_p5 PARTITION OF stock_local FOR VALUES WITH (MODULUS 8, REMAINDER 5);
CREATE TABLE stock_local_p6 PARTITION OF stock_local FOR VALUES WITH (MODULUS 8, REMAINDER 6);
CREATE TABLE stock_local_p7 PARTITION OF stock_local FOR VALUES WITH (MODULUS 8, REMAINDER 7);

-- ---------------------------------------------------------------------------
-- reservas: stock apartado mientras el cajero arma la venta. Con TTL, porque una
-- caja que se cuelga a mitad de venta no puede dejar el stock bloqueado para siempre.
-- Un job libera las vencidas y el stock vuelve solo.
-- ---------------------------------------------------------------------------
CREATE TABLE reservas (
    id          uuid        PRIMARY KEY,
    venta_id    uuid        NOT NULL,
    producto_id uuid        NOT NULL,
    local_id    uuid        NOT NULL,
    cantidad    integer     NOT NULL CHECK (cantidad > 0),
    estado      varchar(20) NOT NULL,
    creada_en   timestamptz NOT NULL DEFAULT now(),
    expira_en   timestamptz NOT NULL,
    cerrada_en  timestamptz
);
COMMENT ON COLUMN reservas.estado IS 'ACTIVA, CONFIRMADA, LIBERADA, VENCIDA';

-- Una reserva por (venta, producto): un reintento del POS no duplica el apartado.
CREATE UNIQUE INDEX ux_reservas_venta_producto ON reservas (venta_id, producto_id);
CREATE INDEX idx_reservas_venta    ON reservas (venta_id);
CREATE INDEX idx_reservas_vencidas ON reservas (expira_en) WHERE estado = 'ACTIVA';

-- ---------------------------------------------------------------------------
-- lotes: la trazabilidad real. La asignacion FEFO (primero el que vence antes,
-- obligatoria en farmacia) se hace al CONFIRMAR, en el consumidor del evento, y no
-- en el camino critico: el cajero no necesita saber de que lote sale hasta el despacho.
-- ---------------------------------------------------------------------------
CREATE TABLE lotes (
    id                  uuid          PRIMARY KEY,
    codigo              varchar(50)   NOT NULL,
    producto_id         uuid          NOT NULL,
    fecha_vencimiento   date          NOT NULL,
    cantidad_recibida   integer       NOT NULL,
    cantidad_disponible integer       NOT NULL CHECK (cantidad_disponible >= 0),
    costo               numeric(12,2),
    local_id            uuid          NOT NULL,
    estado              varchar(20)   NOT NULL DEFAULT 'DISPONIBLE'
);

-- Indice que sirve exactamente a la consulta FEFO: los lotes de este producto en
-- este local, con stock, ordenados por el que vence primero.
CREATE INDEX idx_lotes_fefo ON lotes (producto_id, local_id, fecha_vencimiento)
    WHERE estado = 'DISPONIBLE' AND cantidad_disponible > 0;
CREATE INDEX idx_lotes_vencimiento ON lotes (fecha_vencimiento);

CREATE TABLE movimientos_inventario (
    id          uuid         PRIMARY KEY,
    lote_id     uuid         NOT NULL REFERENCES lotes (id),
    producto_id uuid         NOT NULL,
    local_id    uuid         NOT NULL,
    tipo        varchar(30)  NOT NULL,
    cantidad    integer      NOT NULL,
    usuario_id  uuid         NOT NULL,
    referencia  varchar(200),
    fecha       timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON COLUMN movimientos_inventario.tipo IS 'INGRESO, SALIDA_VENTA, DEVOLUCION, AJUSTE, MERMA';
COMMENT ON COLUMN movimientos_inventario.referencia IS 'venta_id, guia de ingreso u otro documento';

CREATE INDEX idx_movimientos_lote  ON movimientos_inventario (lote_id);
CREATE INDEX idx_movimientos_fecha ON movimientos_inventario (fecha);
CREATE INDEX idx_movimientos_ref   ON movimientos_inventario (referencia);

-- Asignacion de lote a venta, resultado del FEFO. Es la verdad de la trazabilidad:
-- si manana hay alerta sanitaria sobre un lote, esta tabla dice a quien se le vendio.
-- ms-ventas guarda una copia de esto, pero solo para imprimir el comprobante.
CREATE TABLE asignaciones_lote (
    id          uuid        PRIMARY KEY,
    venta_id    uuid        NOT NULL,
    producto_id uuid        NOT NULL,
    lote_id     uuid        NOT NULL REFERENCES lotes (id),
    cantidad    integer     NOT NULL,
    fecha       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_asignaciones_venta ON asignaciones_lote (venta_id);
CREATE INDEX idx_asignaciones_lote  ON asignaciones_lote (lote_id);
