# Proposal: Situación crediticia

## Why

El vendedor hoy no recibe la situación crediticia del prospecto y tiene que rellamar, y `calificacion` la necesita para la `ReglaFinanciacion` del MVP. El change 04 dejó el CUIL cifrado y el consentimiento de precalificación; este change los usa para consultar una fuente externa (simulada en la Primera Parte, el BCRA vía SOAP en la Segunda) sin que una fuente lenta o caída bloquee la calificación ni le devuelva errores. Adapta el change 05 del documento semilla (§6.5) con el mismo criterio "MVP+" del 04: entra en su forma definitiva lo que es caro de cambiar después (forma de la respuesta y estados acordados con `calificacion`, puerto y caché en base), y lo aditivo queda en el backlog.

## What Changes

- `GET /clientes/{id}/situacion-crediticia` responde siempre 200 para un cliente activo, con un `estado` explícito (`CON_DATOS`, `SIN_REGISTROS`, `SIN_CUIL`, `NO_DISPONIBLE`) y, solo con `CON_DATOS`, una `situacion` de 1 a 6, más `periodo`, `fuente` y `consultadaEn`. Nunca incluye el CUIL ni responde 5xx por culpa de la fuente. 404 y 410 como en la ficha.
- El endpoint está reservado a `calificacion`: exige `X-Api-Key` con una clave propia (`CLIENTES_CLAVE_CALIFICACION`). La validación de claves de servicio pasa a ser por consumidor: la clave de `leads` no abre este endpoint ni la de `calificacion` abre el alta.
- Puerto `SituacionCrediticiaPort` con un adaptador simulado determinístico (tabla §5.10 del seed, con demora y falla configurables), envuelto por decorators de caché y de resiliencia (timeout y circuit breaker de Resilience4j).
- Caché en base por cliente con vigencia configurable (30 días): guarda `CON_DATOS` y `SIN_REGISTROS`, nunca `NO_DISPONIBLE`. La anonimización la borra en la misma transacción.
- Enum compartido `EstadoSituacionCrediticia` en `contracts`, con el valor `SIN_CONSENTIMIENTO` reservado.
- Migración `V4__situacion_crediticia.sql`.
- **Desvíos respecto del seed §6.5:**
  - `SIN_CONSENTIMIENTO` existe en el enum pero no se puede producir: por la invariante del 04 (CUIL ⇔ consentimiento vigente) recién aparece con la revocación del backlog `clientes-15`, que suma su rama y su escenario;
  - acceso restringido a `calificacion` por clave de servicio (el seed no protegía el endpoint);
  - el escenario "al restablecerse la fuente, la siguiente consulta reintenta" pasa a "una consulta posterior a la ventana de espera del circuit breaker vuelve a consultar la fuente";
  - en `contracts` va solo el enum, sin un DTO compartido;
  - sin integración con revocación ni reemplazo de CUIL (no existen todavía; el CUIL no cambia una vez registrado).
- **Fuera de alcance:** revocación de consentimiento y `PUT /clientes/{id}/cuil` (`clientes-15`, que deberá invalidar la caché), precarga de la caché después de un alta con CUIL y adaptador SOAP (`clientes-09`), auditoría de accesos al CUIL (`clientes-11`).

## Capabilities

### New Capabilities

- `clientes-situacion-crediticia`: consulta de la situación crediticia de un cliente con estado explícito, caché con vigencia, degradación ante fallas de la fuente, adaptador simulado determinístico y selección de la fuente por configuración.

### Modified Capabilities

- `clientes-transversal`: el acceso restringido a servicios autorizados pasa a ser por consumidor, con una clave para `leads` (alta) y otra para `calificacion` (situación crediticia).
- `clientes-gestion`: la anonimización también elimina la situación crediticia guardada del cliente.

## Impact

- **Código (`clientes-service`):**
  - dominio: `SituacionCrediticia`, `ConsultaSituacion`, puerto `SituacionCrediticiaPort`, `CifradorCuil.descifrar`;
  - aplicación: `SituacionCrediticiaService` y el listener que borra la caché al anonimizar;
  - infraestructura: adaptador simulado y decorators de caché y resiliencia en `infrastructure/precalificacion`, entidad y repositorio de la caché, `descifrar` en `AesGcmCifradorCuil`;
  - API: controller y DTO de respuesta, `@RequiereClaveServicio` con consumidor, `ClaveServicioInterceptor` con una clave por consumidor.
- **Configuración:** `clientes.seguridad.clave-calificacion` y `CLIENTES_CLAVE_CALIFICACION` en `docker-compose.yml` y `.env.example`. Las propiedades `clientes.situacion-crediticia.*` y la instancia `situacionCrediticia` de Resilience4j ya existen (change 01).
- **`contracts`:** nuevo enum `EstadoSituacionCrediticia`.
- **`calificacion` (coordinar con su responsable):** enviar `X-Api-Key`, usar el enum y el mapeo de AC-05 (`CON_DATOS` 1–2 → +20, 3–6 → −25, cualquier otro estado → 0), y un timeout de cliente mayor que el de `clientes` (2 s).
- **Seguridad:** el CUIL se descifra solo en memoria y solo para consultar la fuente; no se loguea ni viaja en la respuesta.
