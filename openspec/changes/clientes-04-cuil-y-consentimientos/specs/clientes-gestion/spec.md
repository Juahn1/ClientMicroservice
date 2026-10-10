# Spec Delta

## MODIFIED Requirements

### Requirement: Consulta de la ficha del cliente
El servicio MUST devolver la ficha de un cliente activo por su ID, con el header `ETag` igual a su versión, la marca no contactar, el CUIL enmascarado, los consentimientos vigentes y la marca de revisión con su motivo.
Si el cliente no existe, MUST responder 404 con `codigo` = `CLIENTE_NO_ENCONTRADO`; si fue anonimizado, MUST responder 410 con `codigo` = `CLIENTE_ANONIMIZADO`.

#### Scenario: Cliente existente
- **WHEN** se consulta `GET /clientes/{id}` de un cliente activo
- **THEN** responde 200 con nombre, apellido, email, teléfono, teléfono alternativo, provincia, localidad, canales, cantidad de contactos, fecha del último contacto, `noContactar`, `noContactarDesde`, `cuil`, `consentimientosVigentes`, `requiereRevision` y `motivoRevision`
- **AND** el header `ETag` contiene la versión actual del cliente

#### Scenario: La versión cambia con un recontacto
- **WHEN** un cliente con `ETag` `W/"0"` recibe un recontacto
- **THEN** `GET /clientes/{id}` devuelve un `ETag` distinto de `W/"0"`

#### Scenario: Cliente inexistente
- **WHEN** se consulta `GET /clientes/{id}` con un ID que nunca existió
- **THEN** responde 404 con `codigo` = `CLIENTE_NO_ENCONTRADO`

#### Scenario: Cliente anonimizado
- **WHEN** se consulta `GET /clientes/{id}` de un cliente anonimizado
- **THEN** responde 410 con `codigo` = `CLIENTE_ANONIMIZADO`
- **AND** el cuerpo no incluye ningún dato del cliente

#### Scenario: Cliente sin revisión pendiente
- **WHEN** se consulta la ficha de un cliente que nunca fue marcado para revisión
- **THEN** `requiereRevision` = false y `motivoRevision` es null

### Requirement: Edición con control de concurrencia
El servicio MUST permitir modificar nombre, apellido, email, teléfono, teléfono alternativo, provincia y localidad de un cliente activo mediante `PATCH /clientes/{id}`, exigiendo el header `If-Match` con la versión vigente.
Los campos ausentes o nulos MUST NOT modificarse, y los informados MUST cumplir las mismas validaciones y normalizaciones que el alta.
Una edición exitosa MUST responder 200 con la ficha y el `ETag` de la nueva versión.

#### Scenario: Edición exitosa
- **WHEN** se envía `PATCH /clientes/{id}` con `If-Match: W/"3"`, apellido "Gómez" y la versión actual es 3
- **THEN** responde 200 con la ficha con apellido "Gómez" y un `ETag` distinto de `W/"3"`
- **AND** el resto de los campos no cambia

#### Scenario: Versión desactualizada
- **WHEN** se envía `PATCH /clientes/{id}` con `If-Match: W/"2"` y la versión actual es 3
- **THEN** responde 412 con `codigo` = `VERSION_DESACTUALIZADA` y el cliente no cambia

#### Scenario: Falta If-Match
- **WHEN** se envía `PATCH /clientes/{id}` sin header `If-Match`
- **THEN** responde 428 con `codigo` = `PRECONDICION_REQUERIDA`

#### Scenario: Edición sin cambios reales
- **WHEN** se envía `PATCH /clientes/{id}` con los mismos valores que ya tiene el cliente
- **THEN** responde 200 y el `ETag` no cambia

#### Scenario: Teléfono normalizado al editar
- **WHEN** se envía `PATCH /clientes/{id}` con teléfono `0351 15 412-3456`
- **THEN** la ficha queda con teléfono `+5493514123456`

#### Scenario: Teléfono alternativo
- **WHEN** se envía `PATCH /clientes/{id}` con teléfono alternativo `11 8765-4321` a un cliente con teléfono `+5491112345678`
- **THEN** responde 200 y la ficha queda con `telefonoAlternativo` = `+5491187654321`

#### Scenario: Teléfono alternativo igual al principal
- **WHEN** se envía `PATCH /clientes/{id}` con teléfono alternativo `11 1234-5678` a un cliente cuyo teléfono principal es `+5491112345678`
- **THEN** responde 422 con `codigo` = `VALIDACION` y un error en el campo `telefonoAlternativo`
- **AND** el cliente no cambia

#### Scenario: Datos inválidos al editar
- **WHEN** se envía `PATCH /clientes/{id}` con apellido "L0pez"
- **THEN** responde 422 con `codigo` = `VALIDACION` y un error en el campo `apellido`
- **AND** el cliente no cambia

#### Scenario: Edición de un cliente inexistente
- **WHEN** se envía `PATCH /clientes/{id}` con un ID que nunca existió
- **THEN** responde 404 con `codigo` = `CLIENTE_NO_ENCONTRADO`

#### Scenario: Edición de un cliente anonimizado
- **WHEN** se envía `PATCH /clientes/{id}` de un cliente anonimizado
- **THEN** responde 410 con `codigo` = `CLIENTE_ANONIMIZADO`

### Requirement: Anonimización a pedido del titular
El servicio MUST atender `POST /clientes/{id}/anonimizacion` eliminando los datos personales del cliente (nombre y apellido reemplazados por `ANONIMIZADO`; email, teléfonos, localidad y CUIL eliminados) y conservando el ID, la provincia, los canales, el contador de contactos y las fechas.
Los consentimientos vigentes MUST quedar revocados con la fecha de la anonimización. La operación MUST responder 204, MUST ser idempotente y MUST liberar el email y el CUIL para un alta futura.

#### Scenario: Anonimización
- **WHEN** se envía `POST /clientes/{id}/anonimizacion` de un cliente activo con su email como confirmación
- **THEN** responde 204
- **AND** `GET /clientes/{id}` responde 410 con `codigo` = `CLIENTE_ANONIMIZADO`
- **AND** el cliente no aparece en la búsqueda

#### Scenario: Datos conservados y eliminados
- **WHEN** se anonimiza un cliente con nombre, apellido, email, teléfono, teléfono alternativo, localidad y CUIL
- **THEN** su registro conserva el ID, la provincia, el canal de origen, el contador de contactos y las fechas
- **AND** no conserva su nombre, apellido, email, teléfonos, localidad ni ninguna forma de su CUIL (cifrada, índice ni últimos dígitos)

#### Scenario: Consentimientos revocados
- **WHEN** se anonimiza un cliente con consentimiento de precalificación vigente
- **THEN** ese consentimiento queda con fecha de revocación igual a la de la anonimización
- **AND** el cliente no tiene ningún consentimiento vigente

#### Scenario: Anonimización repetida
- **WHEN** se envía `POST /clientes/{id}/anonimizacion` de un cliente ya anonimizado
- **THEN** responde 204 y el cliente no cambia

#### Scenario: El email queda libre
- **WHEN** se anonimiza un cliente con email `martina@gmail.com`
- **AND** luego `leads` envía `POST /clientes` con ese email
- **THEN** responde 201 con un `clienteId` distinto

#### Scenario: Anonimización de un cliente inexistente
- **WHEN** se envía `POST /clientes/{id}/anonimizacion` con un ID que nunca existió
- **THEN** responde 404 con `codigo` = `CLIENTE_NO_ENCONTRADO`

## ADDED Requirements

### Requirement: Resolución de la revisión
El servicio MUST permitir que el personal resuelva la marca de revisión enviando `requiereRevision` = false en `PATCH /clientes/{id}`, lo que MUST dejar `requiereRevision` = false y `motivoRevision` en null.
MUST rechazar `requiereRevision` = true con 422 y `codigo` = `VALIDACION`: la marca la pone solo el alta.

#### Scenario: Resolver una revisión
- **WHEN** un cliente tiene `requiereRevision` = true y `motivoRevision` = `CUIL_DISTINTO`
- **AND** se envía `PATCH /clientes/{id}` con su `ETag` y `requiereRevision` = false
- **THEN** responde 200 con `requiereRevision` = false y `motivoRevision` null

#### Scenario: Resolver un cliente sin revisión
- **WHEN** se envía `PATCH /clientes/{id}` con `requiereRevision` = false a un cliente sin marca
- **THEN** responde 200 y el `ETag` no cambia

#### Scenario: Marcar a mano
- **WHEN** se envía `PATCH /clientes/{id}` con `requiereRevision` = true
- **THEN** responde 422 con `codigo` = `VALIDACION` y un error en el campo `requiereRevision`
