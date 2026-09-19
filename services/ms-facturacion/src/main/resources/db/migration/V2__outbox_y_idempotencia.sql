-- Outbox transaccional: el evento se inserta en la MISMA transaccion que el cambio de
-- negocio. Un publicador lo empuja a Kafka despues. Sin esto habria doble escritura
-- (Postgres y Kafka) y una caida entre las dos corrompe el estado en silencio.
CREATE TABLE outbox (
    id           uuid PRIMARY KEY,
    agregado     varchar(50)  NOT NULL,
    agregado_id  uuid         NOT NULL,
    tipo         varchar(80)  NOT NULL,
    clave        varchar(80)  NOT NULL,   -- local_id: define la particion de Kafka
    payload      jsonb        NOT NULL,
    intentos     integer      NOT NULL DEFAULT 0,
    creado_en    timestamptz  NOT NULL DEFAULT now(),
    publicado_en timestamptz
);
CREATE INDEX idx_outbox_pendiente ON outbox (creado_en) WHERE publicado_en IS NULL;

-- Idempotencia del lado consumidor: Kafka garantiza al-menos-una-vez, asi que el
-- consumidor descarta los repetidos por su cuenta.
CREATE TABLE evento_procesado (
    evento_id    uuid        NOT NULL,
    consumidor   varchar(80) NOT NULL,
    procesado_en timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (evento_id, consumidor)
);
