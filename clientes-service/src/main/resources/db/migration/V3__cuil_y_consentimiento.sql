-- Change clientes-04-cuil-y-consentimientos (design D6). El seed la numeraba V2; el change 03 usó ese número.
--   * El CUIL nunca se guarda en claro: cifrado AES-GCM, índice ciego HMAC (unicidad y búsqueda exacta) y los
--     últimos 4 dígitos del DNI para enmascarar sin descifrar. Las tres columnas existen juntas o ninguna.
--   * La invariante "CUIL si y solo si hay consentimiento de precalificación vigente" cruza dos tablas: la
--     garantiza el agregado Cliente, no la base.

ALTER TABLE cliente
    ADD COLUMN cuil_cifrado  BYTEA,
    ADD COLUMN cuil_hash     VARCHAR(64),
    ADD COLUMN cuil_ultimos4 VARCHAR(4);

CREATE UNIQUE INDEX ux_cliente_cuil_activo ON cliente (cuil_hash) WHERE estado = 'ACTIVO';

ALTER TABLE cliente
    ADD CONSTRAINT ck_cliente_cuil_completo
        CHECK ((cuil_cifrado IS NULL) = (cuil_hash IS NULL) AND (cuil_hash IS NULL) = (cuil_ultimos4 IS NULL));

ALTER TABLE cliente DROP CONSTRAINT ck_cliente_anonimizado_sin_datos;
ALTER TABLE cliente
    ADD CONSTRAINT ck_cliente_anonimizado_sin_datos
        CHECK (estado <> 'ANONIMIZADO'
               OR (email IS NULL AND telefono_e164 IS NULL AND telefono_alternativo IS NULL AND localidad IS NULL
                   AND cuil_cifrado IS NULL));

CREATE TABLE consentimiento (
    id            UUID PRIMARY KEY,
    cliente_id    UUID        NOT NULL REFERENCES cliente (id),
    finalidad     VARCHAR(40) NOT NULL,   -- PRECALIFICACION_CREDITICIA (CONTACTO_COMERCIAL llega con el change 07)
    version_texto VARCHAR(20) NOT NULL,   -- versión del texto legal aceptado, ej. "v1"
    origen        VARCHAR(20) NOT NULL,   -- PREFORMULARIO
    otorgado_en   TIMESTAMPTZ NOT NULL,
    revocado_en   TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_consentimiento_vigente ON consentimiento (cliente_id, finalidad) WHERE revocado_en IS NULL;
CREATE INDEX ix_consentimiento_cliente ON consentimiento (cliente_id);
