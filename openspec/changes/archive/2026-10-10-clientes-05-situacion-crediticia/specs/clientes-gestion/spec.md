# Spec Delta

## MODIFIED Requirements

### Requirement: Anonimización a pedido del titular
El servicio MUST atender `POST /clientes/{id}/anonimizacion` eliminando los datos personales del cliente (nombre y apellido reemplazados por `ANONIMIZADO`; email, teléfonos, localidad y CUIL eliminados) y conservando el ID, la provincia, los canales, el contador de contactos y las fechas.
Los consentimientos vigentes MUST quedar revocados y la situación crediticia guardada MUST eliminarse, en la misma operación. La operación MUST responder 204, MUST ser idempotente y MUST liberar el email y el CUIL para un alta futura.

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

#### Scenario: Situación crediticia eliminada
- **WHEN** se anonimiza un cliente que tiene guardada una situación crediticia
- **THEN** el servicio ya no guarda ninguna situación crediticia de ese cliente

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
