-- Change clientes-08-historial-calificaciones (design D4). Historial de las calificaciones que publica calificacion
-- en lead-calificado.
--   * Es una proyección: la fuente de verdad sigue en calificacion. La anonimización borra las filas del cliente en la
--     misma transacción.
--   * nivel y origen sin CHECK: su vocabulario es de calificacion y un valor nuevo no tiene que frenar el consumidor.
--   * lead_id sin FK: el lead es de leads.
--   * La clave natural (lead_id, origen, calculada_en) hace idempotente el consumo: el evento no trae eventId.

CREATE TABLE historial_calificacion (
    id            UUID          PRIMARY KEY,
    cliente_id    UUID          NOT NULL REFERENCES cliente (id),
    lead_id       UUID          NOT NULL,
    nivel         VARCHAR(40)   NOT NULL,
    puntaje       INTEGER       NOT NULL,
    origen        VARCHAR(40)   NOT NULL,
    calculada_en  TIMESTAMPTZ   NOT NULL,
    observacion   VARCHAR(2000),
    motivos       JSONB         NOT NULL DEFAULT '[]',
    registrada_en TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_historial_calificacion UNIQUE (lead_id, origen, calculada_en),
    CONSTRAINT ck_historial_motivos CHECK (jsonb_typeof(motivos) = 'array')
);

CREATE INDEX ix_historial_cliente ON historial_calificacion (cliente_id, calculada_en DESC);
