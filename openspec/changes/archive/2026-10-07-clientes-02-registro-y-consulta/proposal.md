# Proposal: Registro y consulta de clientes

## Why

La misma persona puede completar el preformulario varias veces (otro vehículo, otro día, otro canal). Si cada envío crea un cliente nuevo, la ficha queda partida, el CUIL se guarda repetido y el historial de calificaciones que pidió el equipo (`GET /clientes/{id}/historial`) no tiene nada para acumular. Este change da de alta clientes **deduplicando por email** y permite consultarlos. Combina el documento semilla con el contrato acordado por el equipo (`contratos-backend.md`, v1.1): el contrato define la forma de las entradas y de las respuestas, y el seed define el diseño interno, la seguridad y la escalabilidad.

## What Changes

- `POST /clientes`, llamado solo por `leads` con una **clave de servicio** (`X-Api-Key`). Sin clave válida responde 401.
- Alta con `nombre`, `apellido`, `email` (obligatorio), `telefono` (opcional), `provincia` (obligatoria), `localidad` (opcional) y `canalOrigen` (opcional).
- Deduplicación por email normalizado: 201 si el cliente es nuevo y 200 si ya existía, con `Location` en los dos casos. Un recontacto nunca pisa nombre, apellido ni provincia.
- Normalización del teléfono a E.164 de móvil argentino cuando viene informado.
- `GET /clientes/{id}` con `ETag` de versión.
- `GET /clientes` con búsqueda paginada por teléfono, email, nombre/apellido (sin distinguir mayúsculas ni acentos) y provincia.
- Enums compartidos `Provincia` (24 jurisdicciones) y `CanalOrigen` en un módulo `contracts` nuevo.
- Migración `V1__cliente.sql`.
- Fuera de alcance: CUIL y consentimientos (04, que suma la deduplicación por CUIL), edición y anonimización (03), historial de calificaciones (change nuevo `clientes-08-historial-calificaciones`).

## Capabilities

### New Capabilities

- `clientes-gestion`: alta con deduplicación, ficha del cliente y búsqueda paginada.

### Modified Capabilities

- `clientes-transversal`: suma el acceso restringido por clave de servicio a los endpoints internos de escritura.

## Impact

- **Código:** módulo nuevo `contracts` en el POM padre. En `clientes-service`, las capas `api`, `application`, `domain` e `infrastructure/persistence`, `ClientesProperties` y `CodigoError`.
- **Configuración:** variable nueva `CLIENTES_CLAVE_LEADS` (en `infra/.env.example`, docker-compose y el perfil de test).
- **`leads` (coordinar con su responsable):** manda `X-Api-Key`; separa `nombre` y `apellido`; reenvía `provincia`, `localidad` y `canalOrigen` desde el preformulario; trata 200 y 201 igual (los dos devuelven `clienteId`); propaga los 422 (ProblemDetail con `errores[{campo, mensaje}]`) hacia el front.
- **Gateway:** expone `/api/clientes/**`, reenvía `X-Api-Key` y no la loguea. La protección no depende del gateway.
- **Front:** usa `GET /clientes/{id}`. La lista de valores de `Provincia` resuelve el pendiente del contrato sobre el select de provincia.
