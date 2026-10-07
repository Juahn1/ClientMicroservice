-- Change clientes-02-registro-y-consulta (design D3).
-- Parte de la sección 5.2 del documento semilla con estos desvíos, que vienen del contrato del equipo:
--   * deduplicación por email: índice único sobre email (el teléfono es opcional y se puede compartir);
--   * nombre y apellido separados, más nombre_busqueda para buscar sin mayúsculas ni acentos;
--   * canal_origen y ultimo_canal opcionales (el preformulario todavía no informa el canal).

CREATE TABLE cliente (
    id                     UUID PRIMARY KEY,
    nombre                 VARCHAR(120) NOT NULL,
    apellido               VARCHAR(120) NOT NULL,
    nombre_busqueda        VARCHAR(241) NOT NULL,          -- "nombre apellido" normalizado en Java
    email                  VARCHAR(254),                   -- normalizado; null solo al anonimizar (change 03)
    telefono_e164          VARCHAR(20),
    telefono_alternativo   VARCHAR(20),
    provincia              VARCHAR(40)  NOT NULL,
    localidad              VARCHAR(120),
    canal_origen           VARCHAR(20),
    ultimo_canal           VARCHAR(20),
    contactos_registrados  INT          NOT NULL DEFAULT 1,
    ultimo_contacto_en     TIMESTAMPTZ  NOT NULL,
    requiere_revision      BOOLEAN      NOT NULL DEFAULT FALSE,
    motivo_revision        VARCHAR(40),
    estado                 VARCHAR(15)  NOT NULL DEFAULT 'ACTIVO',  -- ACTIVO | ANONIMIZADO
    version                BIGINT       NOT NULL DEFAULT 0,
    creado_en              TIMESTAMPTZ  NOT NULL,
    actualizado_en         TIMESTAMPTZ  NOT NULL,
    anonimizado_en         TIMESTAMPTZ,
    CONSTRAINT ck_cliente_activo_con_email CHECK (estado <> 'ACTIVO' OR email IS NOT NULL)
);

CREATE UNIQUE INDEX ux_cliente_email_activo    ON cliente (email)                   WHERE estado = 'ACTIVO';
CREATE INDEX        ix_cliente_telefono_activo ON cliente (telefono_e164)           WHERE estado = 'ACTIVO';
CREATE INDEX        ix_cliente_provincia       ON cliente (provincia)               WHERE estado = 'ACTIVO';
CREATE INDEX        ix_cliente_ultimo_contacto ON cliente (ultimo_contacto_en DESC) WHERE estado = 'ACTIVO';
