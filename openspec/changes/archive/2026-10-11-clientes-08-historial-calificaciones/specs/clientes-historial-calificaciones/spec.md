# Spec Delta

## Purpose

Historial de las calificaciones de los leads de un cliente para la ficha del lead en el front: registro idempotente de los eventos `LeadCalificado` que publica `calificacion`, solo para clientes activos y sin frenar el consumo ante mensajes inválidos o fallas, y consulta ordenada del historial.

## ADDED Requirements

### Requirement: Registro de las calificaciones publicadas
El servicio MUST registrar en el historial del cliente cada calificación que `calificacion` publica en el tópico `lead-calificado`, sea un cálculo automático o una corrección manual, con su `leadId`, `nivel`, puntaje, `origen`, fecha de cálculo, `observacion` y `motivos`.
MUST aceptar valores de `nivel` y `origen` que no conozca y registrarlos tal como llegan.

#### Scenario: Calificación automática
- **WHEN** llega un evento de un cliente activo con `leadId` L, `puntos` = 60, `nivel` = `MUY_POTABLE`, `origen` = `AUTOMATICA` y tres motivos
- **THEN** el historial del cliente tiene una calificación del lead L con `puntaje` = 60, `nivel` = `MUY_POTABLE`, `origen` = `AUTOMATICA` y los tres motivos en el mismo orden

#### Scenario: Corrección manual
- **WHEN** el historial tiene la calificación automática del lead L
- **AND** llega un evento del mismo lead con `origen` = `MANUAL`, `nivel` = `POTABLE` y `observacion` = "Anticipo confirmado"
- **THEN** el historial tiene dos calificaciones del lead L, y la manual conserva su `observacion`

#### Scenario: Puntaje negativo
- **WHEN** llega un evento con `puntos` = -25
- **THEN** la calificación queda registrada con `puntaje` = -25

#### Scenario: Nivel desconocido
- **WHEN** llega un evento con `nivel` = `RECONTRA_POTABLE`
- **THEN** la calificación queda registrada con ese nivel

#### Scenario: Sin motivos ni observación
- **WHEN** llega un evento sin `motivos` ni `observacion`
- **THEN** la calificación queda registrada con `motivos` vacío y `observacion` nula

### Requirement: Formato del evento de calificación
El servicio MUST aceptar el evento como un objeto JSON plano con `leadId`, `clienteId`, `puntos`, `nivel`, `origen`, `fecha`, `observacion` y `motivos`, y también envuelto en el sobre común de integración, con esos campos dentro de `payload`.
`leadId`, `clienteId` (UUID), `puntos` (entero), `nivel`, `origen` y `fecha` son obligatorios; `observacion` y `motivos` son opcionales. MUST ignorar los campos que no conozca.

#### Scenario: Evento plano
- **WHEN** llega un evento plano válido de un cliente activo
- **THEN** la calificación queda registrada

#### Scenario: Evento con sobre común
- **WHEN** llega un evento con `eventId`, `eventType` = `LeadCalificado`, `version`, `occurredAt` y los campos de la calificación dentro de `payload`
- **THEN** la calificación queda registrada igual que con el evento plano

#### Scenario: Campos adicionales
- **WHEN** llega un evento válido con un campo `sucursal` que el servicio no conoce
- **THEN** la calificación queda registrada

### Requirement: Fecha de cálculo
Cuando la `fecha` del evento no indica zona horaria, el servicio MUST interpretarla en la zona configurada, por defecto `America/Argentina/Buenos_Aires`. Cuando indica un offset o `Z`, MUST respetarlo.

#### Scenario: Fecha sin zona
- **WHEN** llega un evento con `fecha` = `2026-10-11T15:30:00` y la zona configurada es la de Buenos Aires
- **THEN** la calificación queda con `calculadaEn` = `2026-10-11T18:30:00Z`

#### Scenario: Fecha con offset
- **WHEN** llega un evento con `fecha` = `2026-10-11T15:30:00Z`
- **THEN** la calificación queda con `calculadaEn` = `2026-10-11T15:30:00Z`

### Requirement: Registro idempotente
El servicio MUST considerar la misma calificación a dos eventos con el mismo `leadId`, el mismo `origen` y la misma fecha de cálculo, y MUST registrarla una sola vez aunque el evento llegue repetido.

#### Scenario: Evento repetido
- **WHEN** el mismo evento llega dos veces
- **THEN** el historial tiene una sola calificación de ese evento

#### Scenario: Recálculo del mismo lead
- **WHEN** llegan dos eventos del mismo lead y el mismo `origen` con fechas distintas
- **THEN** el historial tiene las dos calificaciones

### Requirement: Solo se registran calificaciones de clientes activos
El servicio MUST NOT registrar una calificación de un cliente anonimizado ni de un `clienteId` que no existe. Esos eventos MUST descartarse sin frenar la lectura de los siguientes, y el servicio MUST registrar en los logs el motivo del descarte.

#### Scenario: Cliente anonimizado
- **WHEN** llega un evento de un cliente anonimizado
- **AND** después llega un evento de un cliente activo
- **THEN** el historial del cliente anonimizado queda vacío
- **AND** la calificación del cliente activo queda registrada

#### Scenario: Cliente inexistente
- **WHEN** llega un evento con un `clienteId` que nunca existió
- **THEN** no se registra ninguna calificación
- **AND** los logs registran el descarte con el `leadId` y el `clienteId`

#### Scenario: Anonimización simultánea
- **WHEN** llega un evento de un cliente mientras se lo anonimiza
- **THEN** al terminar ambas operaciones el servicio no guarda ninguna calificación de ese cliente

### Requirement: Mensajes que no se pueden procesar
Un mensaje ilegible o inválido MUST enviarse al tópico de mensajes fallidos `lead-calificado.clientes.DLT` sin reintentos. Una falla transitoria al registrar MUST reintentarse con espera creciente y un número acotado de intentos; si persiste, el mensaje MUST enviarse a ese tópico. En ningún caso un mensaje fallido MUST frenar la lectura de los siguientes.

#### Scenario: Mensaje ilegible
- **WHEN** llega un mensaje que no es JSON
- **AND** después llega un evento válido de un cliente activo
- **THEN** el mensaje ilegible queda en `lead-calificado.clientes.DLT` con su contenido original
- **AND** la calificación del evento válido queda registrada

#### Scenario: Falta un campo obligatorio
- **WHEN** llega un evento sin `clienteId`
- **THEN** el mensaje queda en `lead-calificado.clientes.DLT` y no se registra ninguna calificación

#### Scenario: Falla transitoria que se recupera
- **WHEN** el primer intento de registrar un evento válido falla y el siguiente funciona
- **THEN** la calificación queda registrada
- **AND** el mensaje no aparece en `lead-calificado.clientes.DLT`

#### Scenario: Falla persistente
- **WHEN** todos los intentos de registrar un evento válido fallan
- **THEN** el mensaje queda en `lead-calificado.clientes.DLT`
- **AND** el siguiente evento válido queda registrado

### Requirement: Datos financieros fuera de los logs
Al registrar o descartar una calificación, el servicio MUST NOT escribir en los logs los `motivos` ni la `observacion`. Para identificar el mensaje MUST usar solo el `leadId`, el `clienteId`, la partición y el offset.

#### Scenario: Logs del registro
- **WHEN** se registra una calificación cuyo motivo dice "Cobertura aportada: 60.00%" y cuya observación dice "Anticipo confirmado", con logs en nivel DEBUG
- **THEN** ninguna línea de log contiene esos textos

### Requirement: Consulta del historial de calificaciones
El servicio MUST responder `GET /clientes/{id}/historial` de un cliente activo con su `clienteId` y la lista completa de sus calificaciones, con `leadId`, `nivel`, `puntaje`, `origen`, `calculadaEn`, `observacion` y `motivos`, ordenada por `calculadaEn` descendente y sin paginar, y MUST NOT exigir clave de servicio.
Si el cliente no existe MUST responder 404 con `codigo` = `CLIENTE_NO_ENCONTRADO`; si fue anonimizado, 410 con `codigo` = `CLIENTE_ANONIMIZADO`.

#### Scenario: Historial con calificaciones de dos leads
- **WHEN** un cliente tiene una calificación automática del lead A calculada a las 10:00, otra automática del lead B a las 11:00 y una manual del lead A a las 12:00
- **THEN** `GET /clientes/{id}/historial` responde 200 con las tres calificaciones en el orden: manual de A, automática de B, automática de A

#### Scenario: Cliente sin calificaciones
- **WHEN** se consulta el historial de un cliente activo sin calificaciones
- **THEN** responde 200 con `calificaciones` vacía

#### Scenario: Sin clave de servicio
- **WHEN** se consulta el historial sin el header `X-Api-Key`
- **THEN** responde 200

#### Scenario: Cliente inexistente
- **WHEN** se consulta el historial con un ID que nunca existió
- **THEN** responde 404 con `codigo` = `CLIENTE_NO_ENCONTRADO`

#### Scenario: Cliente anonimizado
- **WHEN** se consulta el historial de un cliente anonimizado
- **THEN** responde 410 con `codigo` = `CLIENTE_ANONIMIZADO`
