-- ms-identidad: usuarios, roles, locales, cajas, auditoria.
-- Unico servicio que emite JWT. Expone JWKS para que los demas validen la firma
-- localmente, sin llamarlo en cada request: si identidad se cae, el sistema sigue
-- vendiendo, solo no se puede iniciar sesion nueva.
-- Origen: V1__identidad.sql + V10__auth_social_mfa.sql del monolito, ya fusionadas.

CREATE TABLE roles (
    id          uuid PRIMARY KEY,
    nombre      varchar(50)  NOT NULL UNIQUE,
    descripcion varchar(255) NOT NULL DEFAULT ''
);

CREATE TABLE locales (
    id        uuid PRIMARY KEY,
    nombre    varchar(150) NOT NULL,
    direccion varchar(255),
    activo    boolean      NOT NULL DEFAULT true
);

CREATE TABLE usuarios (
    id              uuid PRIMARY KEY,
    nombre_usuario  varchar(100) NOT NULL UNIQUE,
    password_hash   varchar(255),                 -- null si la cuenta nacio de Google o Facebook
    estado          varchar(20)  NOT NULL,
    local_id        uuid         NOT NULL,
    permisos        varchar(255) NOT NULL DEFAULT '',
    email           varchar(255) UNIQUE,
    proveedor_oauth varchar(30),
    mfa_secret      varchar(64),
    mfa_habilitado  boolean      NOT NULL DEFAULT false
);

CREATE TABLE usuarios_roles (
    usuario_id uuid NOT NULL REFERENCES usuarios (id),
    rol_id     uuid NOT NULL REFERENCES roles (id),
    PRIMARY KEY (usuario_id, rol_id)
);

CREATE TABLE cajas (
    id       uuid PRIMARY KEY,
    nombre   varchar(100) NOT NULL,
    local_id uuid         NOT NULL,
    activa   boolean      NOT NULL DEFAULT true
);

CREATE TABLE sesiones_caja (
    id                 uuid PRIMARY KEY,
    caja_id            uuid           NOT NULL REFERENCES cajas (id),
    usuario_id         uuid           NOT NULL REFERENCES usuarios (id),
    fecha_apertura     timestamptz    NOT NULL,
    monto_inicial      numeric(12, 2) NOT NULL,
    fecha_cierre       timestamptz,
    monto_esperado     numeric(12, 2),
    monto_declarado    numeric(12, 2),
    diferencia         numeric(12, 2),
    observacion_cierre varchar(500),
    estado             varchar(20)    NOT NULL
);
CREATE INDEX idx_sesiones_caja_caja_estado ON sesiones_caja (caja_id, estado);

-- Se alimenta del topico pos.auditoria: cualquier servicio publica, identidad consume.
-- Ya no lleva FK a usuarios: el usuario puede venir de otro servicio.
CREATE TABLE auditoria_operaciones (
    id               uuid          PRIMARY KEY,
    fecha            timestamptz   NOT NULL,
    usuario_id       uuid          NOT NULL,
    servicio         varchar(40)   NOT NULL,
    accion           varchar(100)  NOT NULL,
    entidad          varchar(100)  NOT NULL,
    entidad_id       varchar(100)  NOT NULL,
    detalle          varchar(1000) NOT NULL,
    datos_anteriores text,
    datos_nuevos     text,
    trace_id         varchar(64)
);
CREATE INDEX idx_auditoria_fecha   ON auditoria_operaciones (fecha);
CREATE INDEX idx_auditoria_entidad ON auditoria_operaciones (entidad);

-- Refresh token con rotacion. El access token dura 15 min, no 8 horas: a esta escala
-- una ventana de 8 horas es demasiado ancha para un token robado.
CREATE TABLE refresh_tokens (
    id          uuid         PRIMARY KEY,
    usuario_id  uuid         NOT NULL REFERENCES usuarios (id),
    token_hash  varchar(128) NOT NULL UNIQUE,
    emitido_en  timestamptz  NOT NULL DEFAULT now(),
    expira_en   timestamptz  NOT NULL,
    revocado_en timestamptz
);
CREATE INDEX idx_refresh_usuario ON refresh_tokens (usuario_id) WHERE revocado_en IS NULL;

INSERT INTO roles (id, nombre, descripcion) VALUES
    ('11111111-1111-1111-1111-111111111101', 'ADMINISTRADOR',        'Administra el sistema y supervisa las operaciones'),
    ('11111111-1111-1111-1111-111111111102', 'CAJERO',               'Atiende al cliente y registra ventas en el POS'),
    ('11111111-1111-1111-1111-111111111103', 'QUIMICO_FARMACEUTICO', 'Valida recetas y medicamentos controlados'),
    ('11111111-1111-1111-1111-111111111104', 'ENCARGADO_INVENTARIO', 'Gestiona lotes y existencias'),
    ('11111111-1111-1111-1111-111111111105', 'OPERADOR_CENTRAL',     'Administra informacion compartida entre sedes');
