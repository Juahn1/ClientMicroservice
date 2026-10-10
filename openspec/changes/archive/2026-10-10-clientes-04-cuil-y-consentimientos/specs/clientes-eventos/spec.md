# Spec Delta

## MODIFIED Requirements

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
