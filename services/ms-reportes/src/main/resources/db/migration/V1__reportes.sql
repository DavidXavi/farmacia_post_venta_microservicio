-- ms-reportes: read model puro (CQRS). No tiene endpoints de escritura: todo lo que
-- guarda llega por eventos de Kafka. Es el servicio que mas capacidad de consulta
-- agrega al sistema, porque saca los escaneos analiticos de la base transaccional.
-- Origen: V8__reportes_incentivos.sql, mas las tablas desnormalizadas nuevas.
--
-- Aca esta la compensacion completa del "una base por servicio": se perdio el JOIN
-- entre contextos, y se recupera con tablas planas que el evento ya trae completas.
-- La consulta que antes era un JOIN de tres tablas ahora es un SELECT, y corre mas rapido.

-- ---------------------------------------------------------------------------
-- rm_ventas: la venta ya desnormalizada. Trae adentro el nombre del vendedor y del
-- local porque el evento VentaConfirmada los carga. Cero joins en tiempo de consulta.
-- ---------------------------------------------------------------------------
CREATE TABLE rm_ventas (
    venta_id        uuid          NOT NULL,
    fecha           timestamptz   NOT NULL,
    local_id        uuid          NOT NULL,
    local_nombre    varchar(150)  NOT NULL,
    caja_id         uuid          NOT NULL,
    usuario_id      uuid          NOT NULL,
    usuario_nombre  varchar(150)  NOT NULL,
    cliente_id      uuid,
    cliente_nombre  varchar(300),
    subtotal        numeric(12,2) NOT NULL,
    descuento       numeric(12,2) NOT NULL,
    impuesto        numeric(12,2) NOT NULL,
    total           numeric(12,2) NOT NULL,
    cantidad_lineas integer       NOT NULL,
    estado          varchar(20)   NOT NULL,
    PRIMARY KEY (venta_id, fecha)
) PARTITION BY RANGE (fecha);

CREATE TABLE rm_ventas_2026m01 PARTITION OF rm_ventas FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE rm_ventas_2026m02 PARTITION OF rm_ventas FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE rm_ventas_2026m03 PARTITION OF rm_ventas FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE rm_ventas_2026m04 PARTITION OF rm_ventas FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE rm_ventas_2026m05 PARTITION OF rm_ventas FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE rm_ventas_2026m06 PARTITION OF rm_ventas FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE rm_ventas_2026m07 PARTITION OF rm_ventas FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE rm_ventas_2026m08 PARTITION OF rm_ventas FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE rm_ventas_2026m09 PARTITION OF rm_ventas FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE rm_ventas_2026m10 PARTITION OF rm_ventas FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE rm_ventas_2026m11 PARTITION OF rm_ventas FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE rm_ventas_2026m12 PARTITION OF rm_ventas FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE rm_ventas_futuro  PARTITION OF rm_ventas DEFAULT;

CREATE INDEX idx_rm_ventas_local   ON rm_ventas (local_id, fecha);
CREATE INDEX idx_rm_ventas_usuario ON rm_ventas (usuario_id, fecha);

CREATE TABLE rm_venta_lineas (
    id               uuid          PRIMARY KEY,
    venta_id         uuid          NOT NULL,
    fecha            timestamptz   NOT NULL,
    local_id         uuid          NOT NULL,
    usuario_id       uuid          NOT NULL,
    producto_id      uuid          NOT NULL,
    producto_nombre  varchar(200)  NOT NULL,
    categoria_id     uuid,
    categoria_nombre varchar(150),
    cantidad         integer       NOT NULL,
    precio_unitario  numeric(12,2) NOT NULL,
    descuento        numeric(12,2) NOT NULL,
    total_linea      numeric(12,2) NOT NULL
);
CREATE INDEX idx_rm_lineas_producto ON rm_venta_lineas (producto_id, fecha);
CREATE INDEX idx_rm_lineas_venta    ON rm_venta_lineas (venta_id);

-- Agregado diario, mantenido por el consumidor con UPSERT. El tablero gerencial lee
-- de aca: una fila por dia, local y producto, en vez de agregar millones de lineas.
CREATE TABLE rm_ventas_diarias (
    dia          date          NOT NULL,
    local_id     uuid          NOT NULL,
    producto_id  uuid          NOT NULL,
    unidades     integer       NOT NULL DEFAULT 0,
    importe      numeric(14,2) NOT NULL DEFAULT 0,
    transacciones integer      NOT NULL DEFAULT 0,
    PRIMARY KEY (dia, local_id, producto_id)
);
CREATE INDEX idx_rm_diarias_dia ON rm_ventas_diarias (dia);

-- Incentivos: tambien es calculo derivado de ventas confirmadas, por eso vive aca.
CREATE TABLE reglas_incentivo (
    id               uuid          PRIMARY KEY,
    nombre           varchar(150)  NOT NULL,
    producto_id      uuid,
    categoria_id     uuid,
    monto_por_unidad numeric(12,2) NOT NULL,
    vigencia_inicio  date,
    vigencia_fin     date,
    activa           boolean       NOT NULL DEFAULT true
);

CREATE TABLE incentivos_venta (
    id                 uuid          PRIMARY KEY,
    regla_incentivo_id uuid          NOT NULL REFERENCES reglas_incentivo (id),
    usuario_id         uuid          NOT NULL,
    venta_id           uuid          NOT NULL,
    detalle_venta_id   uuid          NOT NULL,
    cantidad           integer       NOT NULL,
    monto_calculado    numeric(12,2) NOT NULL,
    fecha              timestamptz   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_incentivos_detalle_regla ON incentivos_venta (detalle_venta_id, regla_incentivo_id);
CREATE INDEX idx_incentivos_fecha   ON incentivos_venta (fecha);
CREATE INDEX idx_incentivos_usuario ON incentivos_venta (usuario_id, fecha);
