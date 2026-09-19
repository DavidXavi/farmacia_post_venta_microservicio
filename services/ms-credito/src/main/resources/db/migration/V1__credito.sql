-- ms-credito: dinero. Separado de clientes porque necesita ledger append-only,
-- auditoria y conciliacion, cosas que no le pides a un CRUD de clientes.
-- Su volumen es bajo (el credito es una minoria de las ventas), asi que separarlo
-- no le cuesta latencia al camino caliente.
-- Origen: lineas_credito y movimientos_credito de V5 del monolito.
-- cliente_id ya NO lleva REFERENCES: clientes vive en otra base.

CREATE TABLE lineas_credito (
    id               uuid          PRIMARY KEY,
    cliente_id       uuid          NOT NULL,
    monto_autorizado numeric(12,2) NOT NULL CHECK (monto_autorizado >= 0),
    saldo_disponible numeric(12,2) NOT NULL CHECK (saldo_disponible >= 0),
    reservado        numeric(12,2) NOT NULL DEFAULT 0 CHECK (reservado >= 0),
    vigencia_inicio  date,
    vigencia_fin     date,
    estado           varchar(20)   NOT NULL,
    version          bigint        NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_lineas_credito_cliente ON lineas_credito (cliente_id);

-- Ledger append-only. Nunca se hace UPDATE ni DELETE sobre esta tabla: el saldo es
-- la suma de sus movimientos y eso es lo que permite conciliar y auditar.
-- El trigger de abajo lo impone a nivel de motor, no de disciplina del equipo.
CREATE TABLE movimientos_credito (
    id               uuid          PRIMARY KEY,
    linea_credito_id uuid          NOT NULL REFERENCES lineas_credito (id),
    venta_id         uuid,
    tipo             varchar(20)   NOT NULL,
    monto            numeric(12,2) NOT NULL,
    saldo_resultante numeric(12,2) NOT NULL,
    fecha            timestamptz   NOT NULL DEFAULT now()
);
COMMENT ON COLUMN movimientos_credito.tipo IS 'CARGO, ABONO, RESERVA, LIBERACION, AJUSTE';

CREATE INDEX idx_movimientos_credito_linea ON movimientos_credito (linea_credito_id, fecha);
CREATE INDEX idx_movimientos_credito_venta ON movimientos_credito (venta_id) WHERE venta_id IS NOT NULL;

CREATE OR REPLACE FUNCTION ledger_es_solo_insercion() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'movimientos_credito es un ledger append-only: no admite % ', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_movimientos_credito_inmutable
    BEFORE UPDATE OR DELETE ON movimientos_credito
    FOR EACH ROW EXECUTE FUNCTION ledger_es_solo_insercion();

-- Reserva de credito durante la venta, el equivalente de la reserva de stock.
-- Con TTL, por la misma razon: una caja colgada no puede congelar el credito del cliente.
CREATE TABLE reservas_credito (
    id               uuid          PRIMARY KEY,
    linea_credito_id uuid          NOT NULL REFERENCES lineas_credito (id),
    venta_id         uuid          NOT NULL,
    monto            numeric(12,2) NOT NULL CHECK (monto > 0),
    estado           varchar(20)   NOT NULL,
    creada_en        timestamptz   NOT NULL DEFAULT now(),
    expira_en        timestamptz   NOT NULL,
    cerrada_en       timestamptz
);
CREATE UNIQUE INDEX ux_reservas_credito_venta ON reservas_credito (venta_id);
CREATE INDEX idx_reservas_credito_vencidas ON reservas_credito (expira_en) WHERE estado = 'ACTIVA';
