-- ms-clientes: quien compra y bajo que condiciones. Cliente, su seguro, su convenio
-- y sus recetas son el mismo agregado extendido; separarlos obligaria a tres llamadas
-- para armar una sola venta.
-- Origen: V4__recetas.sql mas la parte de clientes y seguros de V5 del monolito.
-- La linea de credito NO esta aca: se fue a ms-credito porque es dinero y necesita
-- ledger append-only y auditoria propia.

CREATE TABLE clientes (
    id               uuid         PRIMARY KEY,
    dni              varchar(8)   NOT NULL UNIQUE,
    nombres          varchar(150) NOT NULL,
    apellidos        varchar(150) NOT NULL,
    fecha_nacimiento date,
    telefono         varchar(30),
    correo           varchar(150),
    direccion        varchar(250),
    estado           varchar(20)  NOT NULL
);
CREATE INDEX idx_clientes_apellidos ON clientes (apellidos, nombres);

CREATE TABLE convenios_seguro (
    id     uuid         PRIMARY KEY,
    nombre varchar(150) NOT NULL,
    activo boolean      NOT NULL DEFAULT true
);

CREATE TABLE coberturas_seguro (
    id                  uuid         PRIMARY KEY,
    convenio_id         uuid         NOT NULL REFERENCES convenios_seguro (id),
    producto_id         uuid         NOT NULL,
    porcentaje_cubierto numeric(5,2) NOT NULL
);
CREATE INDEX idx_coberturas_convenio ON coberturas_seguro (convenio_id);
CREATE UNIQUE INDEX uq_coberturas_convenio_producto ON coberturas_seguro (convenio_id, producto_id);

CREATE TABLE afiliaciones_cliente (
    id              uuid        PRIMARY KEY,
    cliente_id      uuid        NOT NULL REFERENCES clientes (id),
    convenio_id     uuid        NOT NULL REFERENCES convenios_seguro (id),
    vigencia_inicio date,
    vigencia_fin    date,
    estado          varchar(20) NOT NULL
);
CREATE INDEX idx_afiliaciones_cliente ON afiliaciones_cliente (cliente_id);
CREATE UNIQUE INDEX uq_afiliaciones_cliente_convenio ON afiliaciones_cliente (cliente_id, convenio_id);

CREATE TABLE recetas (
    id                   uuid         PRIMARY KEY,
    numero               varchar(50)  NOT NULL,
    tipo                 varchar(30)  NOT NULL,
    fecha_emision        date         NOT NULL,
    fecha_vencimiento    date,
    producto_id          uuid         NOT NULL,
    cliente_id           uuid REFERENCES clientes (id),
    datos_paciente       varchar(300) NOT NULL,
    datos_profesional    varchar(300) NOT NULL,
    dosis                varchar(300),
    cantidad_autorizada  integer      NOT NULL,
    archivo_respaldo_url varchar(500),
    estado               varchar(20)  NOT NULL,
    retenida_en_botica   boolean      NOT NULL DEFAULT false,
    version              bigint       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_recetas_numero ON recetas (numero);
CREATE INDEX ix_recetas_producto ON recetas (producto_id);
CREATE INDEX ix_recetas_cliente  ON recetas (cliente_id);

CREATE TABLE usos_receta (
    id        uuid        PRIMARY KEY,
    receta_id uuid        NOT NULL REFERENCES recetas (id),
    venta_id  uuid        NOT NULL,
    fecha     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_usos_receta_receta ON usos_receta (receta_id);
CREATE INDEX ix_usos_receta_venta  ON usos_receta (venta_id);
