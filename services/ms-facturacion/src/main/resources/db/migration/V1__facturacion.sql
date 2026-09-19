-- ms-facturacion: el unico servicio que habla con un tercero lento y poco confiable.
-- Aislarlo es la razon de ser del patron: SUNAT caida significa comprobantes
-- encolados, nunca cajas paradas.
-- Se lleva devoluciones y notas de credito porque son el mismo flujo de emision.
-- Origen: tabla comprobantes de V6 y todo V7__anulaciones_notas_credito.sql.

CREATE TABLE comprobantes (
    id             uuid          NOT NULL,
    venta_id       uuid          NOT NULL,
    local_id       uuid          NOT NULL,
    tipo           varchar(20)   NOT NULL,
    serie          varchar(20)   NOT NULL,
    correlativo    integer       NOT NULL,
    monto_total    numeric(12,2) NOT NULL,
    fecha_emision  timestamptz   NOT NULL,
    estado_sunat   varchar(20)   NOT NULL DEFAULT 'PENDIENTE',
    PRIMARY KEY (id, fecha_emision)
) PARTITION BY RANGE (fecha_emision);

COMMENT ON COLUMN comprobantes.tipo IS 'BOLETA, FACTURA, NOTA_CREDITO';
COMMENT ON COLUMN comprobantes.estado_sunat IS 'PENDIENTE, ENVIADO, ACEPTADO, RECHAZADO, OBSERVADO';

CREATE TABLE comprobantes_2026m01 PARTITION OF comprobantes FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE comprobantes_2026m02 PARTITION OF comprobantes FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE comprobantes_2026m03 PARTITION OF comprobantes FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE comprobantes_2026m04 PARTITION OF comprobantes FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE comprobantes_2026m05 PARTITION OF comprobantes FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE comprobantes_2026m06 PARTITION OF comprobantes FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE comprobantes_2026m07 PARTITION OF comprobantes FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE comprobantes_2026m08 PARTITION OF comprobantes FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE comprobantes_2026m09 PARTITION OF comprobantes FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE comprobantes_2026m10 PARTITION OF comprobantes FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE comprobantes_2026m11 PARTITION OF comprobantes FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE comprobantes_2026m12 PARTITION OF comprobantes FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE comprobantes_futuro  PARTITION OF comprobantes DEFAULT;

CREATE UNIQUE INDEX ux_comprobantes_venta      ON comprobantes (venta_id, fecha_emision);
CREATE UNIQUE INDEX ux_comprobantes_serie_corr ON comprobantes (serie, correlativo, fecha_emision);
CREATE INDEX idx_comprobantes_pendientes ON comprobantes (fecha_emision)
    WHERE estado_sunat IN ('PENDIENTE', 'OBSERVADO');

-- ---------------------------------------------------------------------------
-- Correlativo por serie. Una fila por serie, con UPDATE ... RETURNING para que
-- dos pods no emitan el mismo numero. La numeracion de comprobantes no admite
-- huecos ni repetidos ante SUNAT, asi que no vale un contador en memoria.
-- ---------------------------------------------------------------------------
CREATE TABLE series_comprobante (
    serie       varchar(20) PRIMARY KEY,
    tipo        varchar(20) NOT NULL,
    local_id    uuid        NOT NULL,
    ultimo      integer     NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------------------
-- Cada intento contra SUNAT, con su respuesta. Es la bitacora que se revisa
-- cuando el contador pregunta por que un comprobante salio observado.
-- ---------------------------------------------------------------------------
CREATE TABLE envios_sunat (
    id             uuid        PRIMARY KEY,
    comprobante_id uuid        NOT NULL,
    intento        integer     NOT NULL,
    enviado_en     timestamptz NOT NULL DEFAULT now(),
    respondido_en  timestamptz,
    codigo_sunat   varchar(20),
    mensaje        varchar(1000),
    exitoso        boolean     NOT NULL DEFAULT false
);
CREATE INDEX idx_envios_comprobante ON envios_sunat (comprobante_id, intento);

CREATE TABLE devoluciones (
    id         uuid         PRIMARY KEY,
    venta_id   uuid         NOT NULL,
    local_id   uuid         NOT NULL,
    usuario_id uuid         NOT NULL,
    motivo     varchar(500) NOT NULL,
    fecha      timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_devoluciones_venta ON devoluciones (venta_id);

CREATE TABLE detalle_devoluciones (
    id               uuid          PRIMARY KEY,
    devolucion_id    uuid          NOT NULL REFERENCES devoluciones (id),
    detalle_venta_id uuid          NOT NULL,
    producto_id      uuid          NOT NULL,
    cantidad         integer       NOT NULL CHECK (cantidad > 0),
    monto_devuelto   numeric(12,2) NOT NULL
);
CREATE INDEX idx_detalle_devoluciones_devolucion ON detalle_devoluciones (devolucion_id);

CREATE TABLE notas_credito (
    id             uuid          PRIMARY KEY,
    venta_id       uuid          NOT NULL,
    comprobante_id uuid          NOT NULL,
    local_id       uuid          NOT NULL,
    usuario_id     uuid          NOT NULL,
    motivo         varchar(500)  NOT NULL,
    monto_total    numeric(12,2) NOT NULL,
    fecha          timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX idx_notas_credito_venta ON notas_credito (venta_id);

INSERT INTO series_comprobante (serie, tipo, local_id, ultimo) VALUES
    ('B001', 'BOLETA',       '11111111-1111-1111-1111-111111111001', 0),
    ('F001', 'FACTURA',      '11111111-1111-1111-1111-111111111001', 0),
    ('BC01', 'NOTA_CREDITO', '11111111-1111-1111-1111-111111111001', 0);
