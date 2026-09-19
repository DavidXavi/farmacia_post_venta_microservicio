-- ms-ventas: el orquestador. No guarda catalogo ni stock, guarda la venta y coordina
-- la saga. Particionado por mes porque crece sin parar y casi todas las consultas
-- son del mes en curso; las particiones viejas se mueven a almacenamiento frio.
-- Origen: V6__ventas_pagos.sql del monolito, sin la tabla comprobantes (se fue a
-- ms-facturacion, junto con todo lo que habla con SUNAT).

CREATE TABLE formas_pago (
    id     uuid         PRIMARY KEY,
    nombre varchar(100) NOT NULL,
    tipo   varchar(30)  NOT NULL,
    activo boolean      NOT NULL DEFAULT true
);

-- ---------------------------------------------------------------------------
-- ventas, particionada por mes de la fecha.
-- caja_id, sesion_caja_id, usuario_id, cliente_id, convenio_seguro_id y
-- linea_credito_id son ids de OTROS servicios: columna uuid simple, sin FK.
-- La consistencia la garantiza la saga, no el motor.
-- ---------------------------------------------------------------------------
CREATE TABLE ventas (
    id                 uuid        NOT NULL,
    fecha              timestamptz NOT NULL,
    local_id           uuid        NOT NULL,
    caja_id            uuid        NOT NULL,
    sesion_caja_id     uuid        NOT NULL,
    usuario_id         uuid        NOT NULL,
    cliente_id         uuid,
    convenio_seguro_id uuid,
    linea_credito_id   uuid,
    estado             varchar(20) NOT NULL,
    numero_correlativo bigint,
    version            bigint      NOT NULL DEFAULT 0,
    PRIMARY KEY (id, fecha)
) PARTITION BY RANGE (fecha);

COMMENT ON COLUMN ventas.estado IS 'BORRADOR, CONFIRMADA, ANULADA';

-- Particiones iniciales. En produccion las crea pg_partman; aca van explicitas para
-- que el proyecto arranque sin extensiones extra.
CREATE TABLE ventas_2026m01 PARTITION OF ventas FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE ventas_2026m02 PARTITION OF ventas FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE ventas_2026m03 PARTITION OF ventas FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE ventas_2026m04 PARTITION OF ventas FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE ventas_2026m05 PARTITION OF ventas FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE ventas_2026m06 PARTITION OF ventas FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE ventas_2026m07 PARTITION OF ventas FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE ventas_2026m08 PARTITION OF ventas FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE ventas_2026m09 PARTITION OF ventas FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE ventas_2026m10 PARTITION OF ventas FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE ventas_2026m11 PARTITION OF ventas FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE ventas_2026m12 PARTITION OF ventas FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE ventas_futuro  PARTITION OF ventas DEFAULT;

CREATE INDEX idx_ventas_local_fecha ON ventas (local_id, fecha);
CREATE INDEX idx_ventas_caja        ON ventas (caja_id, fecha);
CREATE INDEX idx_ventas_cliente     ON ventas (cliente_id) WHERE cliente_id IS NOT NULL;

-- Las hijas no se particionan: se acceden siempre por venta_id.
CREATE TABLE detalles_venta (
    id                    uuid          PRIMARY KEY,
    venta_id              uuid          NOT NULL,
    producto_id           uuid          NOT NULL,
    cantidad              integer       NOT NULL CHECK (cantidad > 0),
    precio_unitario       numeric(12,2) NOT NULL,
    tasa_impuesto         numeric(5,2)  NOT NULL,
    promocion_aplicada_id uuid,
    receta_id             uuid,
    descuento_monto       numeric(12,2) NOT NULL DEFAULT 0,
    nombre_producto       varchar(200)  NOT NULL
);
COMMENT ON COLUMN detalles_venta.precio_unitario IS
    'Copia deliberada: el precio de la venta es el del momento de la venta, aunque el catalogo cambie manana.';
COMMENT ON COLUMN detalles_venta.nombre_producto IS
    'Copia deliberada: el comprobante debe seguir imprimiendose aunque el producto ya no exista en catalogo.';

CREATE INDEX idx_detalles_venta_venta ON detalles_venta (venta_id);

-- Copia de la asignacion FEFO que hace ms-inventario, solo para imprimir el
-- comprobante. La verdad de la trazabilidad vive en asignaciones_lote, en pg_inventario.
CREATE TABLE detalle_venta_lotes (
    id               uuid    PRIMARY KEY,
    detalle_venta_id uuid    NOT NULL REFERENCES detalles_venta (id),
    lote_id          uuid    NOT NULL,
    cantidad_tomada  integer NOT NULL
);
CREATE INDEX idx_detalle_venta_lotes_detalle ON detalle_venta_lotes (detalle_venta_id);

CREATE TABLE pagos (
    id                  uuid          PRIMARY KEY,
    venta_id            uuid          NOT NULL,
    forma_pago_id       uuid          NOT NULL REFERENCES formas_pago (id),
    monto               numeric(12,2) NOT NULL,
    codigo_autorizacion varchar(100),
    fecha               timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX idx_pagos_venta ON pagos (venta_id);

-- ---------------------------------------------------------------------------
-- Estado de la saga. Sin esta tabla, una venta que quedo a medias porque un
-- consumidor fallo es invisible: no se sabe si hay que compensar ni que paso.
-- ---------------------------------------------------------------------------
CREATE TABLE saga_venta (
    venta_id           uuid        PRIMARY KEY,
    estado             varchar(30) NOT NULL,
    stock_confirmado   boolean     NOT NULL DEFAULT false,
    credito_confirmado boolean     NOT NULL DEFAULT false,
    comprobante_emitido boolean    NOT NULL DEFAULT false,
    motivo_compensacion varchar(500),
    iniciada_en        timestamptz NOT NULL DEFAULT now(),
    cerrada_en         timestamptz
);
COMMENT ON COLUMN saga_venta.estado IS 'EN_CURSO, COMPLETADA, COMPENSANDO, COMPENSADA';

CREATE INDEX idx_saga_abiertas ON saga_venta (iniciada_en) WHERE cerrada_en IS NULL;

INSERT INTO formas_pago (id, nombre, tipo, activo) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Efectivo',            'EFECTIVO',          true),
    ('00000000-0000-0000-0000-000000000002', 'Tarjeta de debito',   'TARJETA_DEBITO',    true),
    ('00000000-0000-0000-0000-000000000003', 'Tarjeta de credito',  'TARJETA_CREDITO',   true),
    ('00000000-0000-0000-0000-000000000004', 'Transferencia',       'TRANSFERENCIA',     true),
    ('00000000-0000-0000-0000-000000000005', 'Billetera digital',   'BILLETERA_DIGITAL', true),
    ('00000000-0000-0000-0000-000000000006', 'Copago de seguro',    'COPAGO_SEGURO',     true),
    ('00000000-0000-0000-0000-000000000007', 'Credito de farmacia', 'CREDITO_FARMACIA',  true),
    ('00000000-0000-0000-0000-000000000008', 'Otro',                'OTRO',              true);
