-- Change clientes-05-situacion-crediticia (design D4). El seed la numeraba V3; los changes 03 y 04 usaron V2 y V3.
--   * Caché en base (S-01): las réplicas la comparten. Una fila por cliente, reemplazada en cada consulta a la fuente.
--   * Solo se guarda lo que informó la fuente (CON_DATOS o SIN_REGISTROS): un NO_DISPONIBLE se reintenta en la
--     próxima consulta. La base lo garantiza aunque el código se equivoque.
--   * vigente_hasta = consultada_en + vigencia configurada al momento de guardar.
--   * La anonimización borra la fila en la misma transacción (design D9).

CREATE TABLE situacion_crediticia (
    cliente_id    UUID        PRIMARY KEY REFERENCES cliente (id),
    estado        VARCHAR(20) NOT NULL,
    situacion     SMALLINT,
    periodo       VARCHAR(6)  NOT NULL,   -- AAAAMM informado por la fuente
    fuente        VARCHAR(20) NOT NULL,   -- SIMULADA | BCRA
    consultada_en TIMESTAMPTZ NOT NULL,
    vigente_hasta TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_situacion_estado CHECK (estado IN ('CON_DATOS', 'SIN_REGISTROS')),
    CONSTRAINT ck_situacion_valor CHECK ((estado = 'CON_DATOS') = (situacion IS NOT NULL)
                                         AND (situacion IS NULL OR situacion BETWEEN 1 AND 6)),
    CONSTRAINT ck_situacion_periodo CHECK (periodo ~ '^[0-9]{4}(0[1-9]|1[0-2])$')
);
