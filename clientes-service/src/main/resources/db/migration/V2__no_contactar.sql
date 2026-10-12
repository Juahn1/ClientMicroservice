-- Change clientes-03-edicion-y-baja (design D3).
--   * no_contactar es un atributo y no un estado: el cliente sigue ACTIVO y la deduplicación por email
--     (ux_cliente_email_activo) lo sigue encontrando, así que un recontacto no crea un duplicado.
--   * ck_cliente_anonimizado_sin_datos es defensa en profundidad: si una anonimización olvida un dato personal,
--     falla en la base en lugar de dejarlo guardado.
-- El seed reservaba V2 para el CUIL; esa migración del change 04 pasa a V3.

ALTER TABLE cliente
    ADD COLUMN no_contactar       BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN no_contactar_desde TIMESTAMPTZ;

ALTER TABLE cliente
    ADD CONSTRAINT ck_cliente_no_contactar_fecha
        CHECK (no_contactar = (no_contactar_desde IS NOT NULL)),
    ADD CONSTRAINT ck_cliente_anonimizado_sin_datos
        CHECK (estado <> 'ANONIMIZADO'
               OR (email IS NULL AND telefono_e164 IS NULL AND telefono_alternativo IS NULL AND localidad IS NULL));
