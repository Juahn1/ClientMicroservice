# clientes-eventos Specification

## Purpose

Eventos de integración que publica el servicio clientes para que otros servicios (en particular `leads`) se enteren de cambios en la ficha de un cliente, sin recibir sus datos personales.

## Requirements

### Requirement: Publicación de ClienteActualizado
Cuando una edición o la marca no contactar modifican al menos un campo del cliente, el servicio MUST publicar `ClienteActualizado` en el tópico `cliente-actualizado` con clave `clienteId`, después de confirmar la transacción.
El payload MUST indicar los nombres de los campos modificados: `NOMBRE`, `APELLIDO`, `EMAIL`, `TELEFONO`, `PROVINCIA`, `LOCALIDAD` o `NO_CONTACTAR`.
Los cambios de teléfono alternativo, de la marca de revisión, del CUIL y de los consentimientos MUST NOT publicar eventos.

#### Scenario: Cambio de nombre y email
- **WHEN** un `PATCH /clientes/{id}` modifica nombre y email
- **THEN** se publica `ClienteActualizado` con clave igual al `clienteId` y `campos` = [`NOMBRE`, `EMAIL`]

#### Scenario: Marca no contactar
- **WHEN** se marca como no contactar a un cliente que no tenía la marca
- **THEN** se publica `ClienteActualizado` con `campos` = [`NO_CONTACTAR`]

#### Scenario: Quitar la marca no contactar
- **WHEN** se quita la marca no contactar de un cliente marcado
- **THEN** se publica `ClienteActualizado` con `campos` = [`NO_CONTACTAR`]

#### Scenario: Operación sin cambios reales
- **WHEN** un `PATCH` envía los mismos valores que ya tenía el cliente, o se repite una marca ya aplicada
- **THEN** no se publica ningún evento

#### Scenario: Operación rechazada
- **WHEN** una edición responde 409, 412, 422 o 428
- **THEN** no se publica ningún evento

#### Scenario: Recontacto
- **WHEN** un `POST /clientes` coincide con un cliente existente, aunque le quite la marca no contactar, le registre el CUIL o lo marque para revisión
- **THEN** no se publica ningún evento

#### Scenario: Edición de datos que no copia leads
- **WHEN** un `PATCH /clientes/{id}` modifica solo el teléfono alternativo o resuelve la marca de revisión
- **THEN** responde 200 con un `ETag` nuevo y no se publica ningún evento

#### Scenario: Edición mixta
- **WHEN** un `PATCH /clientes/{id}` modifica el apellido y el teléfono alternativo
- **THEN** se publica `ClienteActualizado` con `campos` = [`APELLIDO`]

### Requirement: Publicación de ClienteAnonimizado
Al anonimizar un cliente activo, el servicio MUST publicar `ClienteAnonimizado` en el tópico `cliente-anonimizado` con clave `clienteId`, después de confirmar la transacción.

#### Scenario: Evento de anonimización
- **WHEN** se anonimiza un cliente activo
- **THEN** se publica un `ClienteAnonimizado` con su `clienteId`

#### Scenario: Anonimización repetida
- **WHEN** se anonimiza un cliente que ya estaba anonimizado
- **THEN** no se publica ningún evento

### Requirement: Eventos sin datos personales
Los eventos que publica el servicio MUST usar el sobre común de integración (`eventId`, `eventType`, `version`, `occurredAt`, `correlationId`, `payload`) y MUST NOT incluir nombre, apellido, teléfono, email ni CUIL.

#### Scenario: Revisión del payload
- **WHEN** se publica cualquier evento del servicio
- **THEN** el payload contiene solo el `clienteId` y, si corresponde, los nombres de los campos modificados
- **AND** ningún valor del mensaje contiene el nombre, el email ni el teléfono del cliente

#### Scenario: CorrelationId del request
- **WHEN** un request con `X-Correlation-Id: abc-123` produce un evento
- **THEN** el evento tiene `correlationId` = `abc-123`

### Requirement: La publicación no altera la operación
Una falla al publicar un evento MUST NOT revertir la operación ya confirmada ni cambiar su respuesta HTTP; el servicio MUST registrarla en los logs con el `clienteId` y el tipo de evento.

#### Scenario: Broker no disponible
- **WHEN** el broker de mensajería no está disponible y se edita un cliente
- **THEN** la edición responde 200 y queda guardada
- **AND** los logs registran la falla de publicación con el `clienteId`
