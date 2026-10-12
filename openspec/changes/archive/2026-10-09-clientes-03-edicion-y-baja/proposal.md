# Proposal: Edición, no contactar y anonimización de clientes

## Why

El telemarketer, el vendedor y el dueño de la agencia necesitan corregir los datos de un cliente sin pisar los cambios de un compañero. Cuando un cliente pide que no lo llamen más, el negocio tiene que dejar de contactarlo **sin perder la ficha**: si la persona vuelve a consultar, se reutiliza el mismo registro con su historial y su contador de contactos. La Ley 25.326 (art. 16) además obliga a poder suprimir los datos personales cuando el titular lo pide. Este change adapta el change 03 del documento semilla (§6.3) al contrato del equipo (`contratos-backend.md`, v1.1) y a lo que dejó el change 02: la deduplicación es por email (no por teléfono), el nombre está separado en nombre y apellido, y el alta la hace `leads`.

## What Changes

- `PATCH /clientes/{id}` con control de concurrencia optimista (`If-Match` / `ETag`). Edita nombre, apellido, email, teléfono, provincia y localidad. Un email que pertenece a otro cliente activo responde 409 `EMAIL_EN_USO`.
- Marca **no contactar**: `PUT /clientes/{id}/no-contactar` la activa y `DELETE /clientes/{id}/no-contactar` la quita. Son idempotentes y no piden `If-Match`. El cliente sigue `ACTIVO`: es un atributo, no un estado, así que la deduplicación por email no cambia.
- El recontacto (`POST /clientes` que coincide por email) **limpia la marca no contactar**, sin publicar evento.
- `POST /clientes/{id}/anonimizacion` con el email del cliente en el cuerpo como confirmación. Anula los datos personales, libera el email para un alta futura y es idempotente.
- **BREAKING (para quien consuma `GET /clientes/{id}`):** un cliente anonimizado responde **410** `CLIENTE_ANONIMIZADO` en lugar de 404.
- La ficha y el resumen de búsqueda incluyen `noContactar`.
- Eventos de integración `ClienteActualizado` (con los nombres de los campos modificados) y `ClienteAnonimizado`, publicados después del commit, sin datos personales y con el `correlationId` del request.
- Migración `V2__no_contactar.sql`.
- **Desvíos respecto del seed §6.3:**
  - el 409 pasa de `TELEFONO_EN_USO` a `EMAIL_EN_USO`;
  - la baja es `POST .../anonimizacion` con confirmación, no `DELETE /clientes/{id}`;
  - se agrega la marca no contactar;
  - la edición de `requiereRevision` pasa al change 04, que es el que la activa (`CUIL_DISTINTO`).
- **Fuera de alcance:**
  - borrar CUIL, consentimientos y situación crediticia al anonimizar: cada change que los agregue (04 y 05) extiende la anonimización;
  - autenticación de usuarios (Integrador);
  - outbox (backlog `clientes-10`).

## Capabilities

### New Capabilities

- `clientes-eventos`: eventos de integración que publica el servicio (`ClienteActualizado`, `ClienteAnonimizado`), su sobre común y la garantía de no incluir datos personales.

### Modified Capabilities

- `clientes-gestion`: suma edición con control de concurrencia, marca no contactar y anonimización; la ficha de un cliente anonimizado pasa a 410; el recontacto limpia la marca; la ficha y la búsqueda exponen `noContactar`.
- `clientes-transversal`: el acceso restringido por clave de servicio aclara qué endpoints la exigen (solo el alta) y cuáles usa el personal del negocio desde el front sin clave.

## Impact

- **Código:**
  - `clientes-service`:
    - `Cliente` (editar, marcar o desmarcar no contactar, anonimizar) y eventos de dominio;
    - servicios `EdicionClienteService`, `NoContactarService` y `AnonimizacionClienteService`;
    - listener AFTER_COMMIT y publicador Kafka;
    - `ClienteController`, `CodigoError` y `ApiExceptionHandler`, más la persistencia.
  - `contracts`: sobre de evento y records de `ClienteActualizado` y `ClienteAnonimizado` en `com.uade.crm.contracts.clientes`.
- **Infraestructura:** tópicos `cliente-actualizado` y `cliente-anonimizado` (creados por el servicio). El broker ya está en `infra/docker-compose.yml`.
- **`leads` (coordinar con su responsable):**
  - consumir `cliente-actualizado` para refrescar su copia (si la mantiene) y sacar de la cola a los clientes con `NO_CONTACTAR`;
  - consumir `cliente-anonimizado`;
  - tratar el 410 de `GET /clientes/{id}`.
- **Front y contrato del equipo:** sumar `PATCH`, `no-contactar` y `anonimizacion` a `contratos-backend.md`. El front envía `If-Match` con el `ETag` de la ficha y, opcionalmente, `X-Usuario`.
- **Seguridad:** sin autenticación en la Primera Parte, los nuevos endpoints quedan accesibles a quien llegue al gateway (igual que los `GET`). Es un riesgo aceptado y documentado en `design.md`.
