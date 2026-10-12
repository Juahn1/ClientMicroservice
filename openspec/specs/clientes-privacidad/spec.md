# clientes-privacidad Specification

## Purpose

Tratamiento del CUIL del cliente conforme a la Ley 25.326: validación, guardado cifrado, enmascarado, consentimiento de precalificación crediticia y deduplicación del alta por CUIL, sin exponer nunca el CUIL completo.

## Requirements

### Requirement: Validación del CUIL
Cuando el alta informa un CUIL, el servicio MUST aceptarlo con o sin guiones y normalizarlo a 11 dígitos.
MUST rechazarlo con 422 y `codigo` = `CUIL_INVALIDO`, con un error en el campo `cuil`, si no tiene 11 dígitos, si su prefijo no es de persona humana (20, 23, 24 o 27) o si su dígito verificador por módulo 11 no es correcto.

#### Scenario: CUIL válido con guiones
- **WHEN** `leads` envía `POST /clientes` con CUIL `20-12345678-6` y `consentimiento` = true
- **THEN** responde 201 y la ficha del cliente muestra `cuil` = `XX-XXXX5678-X`

#### Scenario: CUIL válido sin guiones
- **WHEN** `leads` envía `POST /clientes` con CUIL `27301112225` y `consentimiento` = true
- **THEN** responde 201

#### Scenario: Dígito verificador incorrecto
- **WHEN** `leads` envía `POST /clientes` con CUIL `20-12345678-5`
- **THEN** responde 422 con `codigo` = `CUIL_INVALIDO` y un error en el campo `cuil`
- **AND** no se crea ni se modifica ningún cliente

#### Scenario: Prefijo de persona jurídica
- **WHEN** `leads` envía `POST /clientes` con CUIL `30-71234567-1`
- **THEN** responde 422 con `codigo` = `CUIL_INVALIDO`

#### Scenario: Largo incorrecto
- **WHEN** `leads` envía `POST /clientes` con CUIL `20-1234567-6`
- **THEN** responde 422 con `codigo` = `CUIL_INVALIDO`

### Requirement: El CUIL requiere consentimiento de precalificación
El servicio MUST NOT registrar un CUIL si el mismo request no trae `consentimiento` = true, y MUST rechazar un consentimiento que llegue sin CUIL.
En ambos casos MUST responder 422 sin crear ni modificar ningún cliente.

#### Scenario: CUIL sin consentimiento
- **WHEN** `leads` envía `POST /clientes` con un CUIL válido y sin `consentimiento`
- **THEN** responde 422 con `codigo` = `CONSENTIMIENTO_REQUERIDO`
- **AND** no se crea ningún cliente

#### Scenario: CUIL con consentimiento rechazado
- **WHEN** `leads` envía `POST /clientes` con un CUIL válido y `consentimiento` = false
- **THEN** responde 422 con `codigo` = `CONSENTIMIENTO_REQUERIDO`

#### Scenario: Consentimiento sin CUIL
- **WHEN** `leads` envía `POST /clientes` con `consentimiento` = true y sin `cuil`
- **THEN** responde 422 con `codigo` = `VALIDACION` y un error en el campo `consentimiento`

### Requirement: Registro del consentimiento de precalificación
Al registrar un CUIL, el servicio MUST registrar un consentimiento con finalidad `PRECALIFICACION_CREDITICIA`, la versión configurada del texto aceptado, origen `PREFORMULARIO` y la fecha de recepción.
Un cliente MUST tener como máximo un consentimiento vigente por finalidad, y MUST tener CUIL si y solo si tiene vigente el de precalificación.

#### Scenario: Consentimiento en la ficha
- **WHEN** se da de alta un cliente con CUIL y `consentimiento` = true
- **THEN** la ficha incluye en `consentimientosVigentes` un elemento con `finalidad` = `PRECALIFICACION_CREDITICIA`, `versionTexto` = `v1` y `otorgadoEn`

#### Scenario: Recontacto con el consentimiento ya vigente
- **WHEN** un cliente tiene CUIL y consentimiento de precalificación otorgado el 2026-10-01
- **AND** `leads` envía `POST /clientes` con el mismo CUIL y `consentimiento` = true
- **THEN** la ficha sigue mostrando un único consentimiento de precalificación con `otorgadoEn` del 2026-10-01

#### Scenario: Recontacto sin CUIL
- **WHEN** un cliente tiene CUIL y consentimiento de precalificación
- **AND** `leads` envía `POST /clientes` con su email y sin `cuil` ni `consentimiento`
- **THEN** responde 200 y el cliente conserva su CUIL y su consentimiento

### Requirement: Protección del CUIL
El servicio MUST guardar el CUIL cifrado y MUST NOT devolverlo completo en ninguna respuesta.
La ficha MUST mostrarlo como `XX-XXXX` seguido de los últimos 4 dígitos del DNI y `-X`, o null si el cliente no tiene CUIL; los resultados de búsqueda MUST NOT incluirlo.

#### Scenario: CUIL en la base
- **WHEN** se registra el CUIL `20-12345678-6`
- **THEN** ninguna columna de la tabla `cliente` ni de la tabla `consentimiento` contiene el texto `20123456786`

#### Scenario: CUIL en la ficha
- **WHEN** se consulta la ficha de un cliente con CUIL `20-12345678-6`
- **THEN** el campo `cuil` vale `XX-XXXX5678-X`

#### Scenario: Cliente sin CUIL
- **WHEN** se consulta la ficha de un cliente dado de alta sin CUIL
- **THEN** `cuil` es null y `consentimientosVigentes` es una lista vacía

#### Scenario: CUIL fuera de la búsqueda
- **WHEN** se consulta `GET /clientes` y entre los resultados hay un cliente con CUIL
- **THEN** ningún elemento de `contenido` incluye el campo `cuil`

### Requirement: Deduplicación por CUIL
Cuando el alta informa un CUIL que pertenece a un cliente activo, el servicio MUST resolverla como recontacto de ese cliente, aunque el email sea distinto, respondiendo 200 con `coincidencia` = `CUIL`.
MUST NOT reemplazar el email registrado por el informado.

#### Scenario: Mismo CUIL con otro email
- **WHEN** existe un cliente con CUIL `27-30111222-5`, email `martina@gmail.com` y teléfono `+5491112345678`
- **AND** `leads` envía `POST /clientes` con ese CUIL, `consentimiento` = true, email `martu.lopez@gmail.com` y teléfono `11 8765-4321`
- **THEN** responde 200 con el mismo `clienteId`, `nuevo` = false y `coincidencia` = `CUIL`
- **AND** el cliente conserva el email `martina@gmail.com` y el teléfono `+5491112345678`
- **AND** queda con `telefonoAlternativo` = `+5491187654321` y `contactosRegistrados` incrementado en 1

#### Scenario: Mismo CUIL y mismo email
- **WHEN** existe un cliente con CUIL `27-30111222-5` y email `martina@gmail.com`
- **AND** `leads` envía `POST /clientes` con ese CUIL, `consentimiento` = true y email `martina@gmail.com`
- **THEN** responde 200 con el mismo `clienteId` y `coincidencia` = `CUIL`
- **AND** el cliente no queda marcado con `requiereRevision`

#### Scenario: Altas simultáneas con el mismo CUIL
- **WHEN** llegan dos `POST /clientes` concurrentes con el mismo CUIL, `consentimiento` = true y emails distintos que no pertenecen a ningún cliente
- **THEN** existe un único cliente activo con ese CUIL
- **AND** ambas respuestas devuelven el mismo `clienteId`

#### Scenario: El CUIL queda libre al anonimizar
- **WHEN** se anonimiza un cliente con CUIL `20-12345678-6`
- **AND** luego `leads` envía `POST /clientes` con ese CUIL, `consentimiento` = true y un email nuevo
- **THEN** responde 201 con un `clienteId` distinto

### Requirement: Registro del CUIL en un recontacto por email
Cuando el alta coincide por email con un cliente activo sin CUIL y el CUIL informado no pertenece a ningún cliente activo, el servicio MUST registrar el CUIL y el consentimiento en ese cliente.

#### Scenario: Cliente que vuelve pidiendo financiación
- **WHEN** existe un cliente con email `martina@gmail.com` y sin CUIL
- **AND** `leads` envía `POST /clientes` con ese email, CUIL `20-12345678-6` y `consentimiento` = true
- **THEN** responde 200 con el mismo `clienteId` y `coincidencia` = `EMAIL`
- **AND** la ficha muestra `cuil` = `XX-XXXX5678-X` y el consentimiento de precalificación vigente

### Requirement: Un CUIL distinto no reemplaza al registrado
Cuando el alta coincide por email con un cliente que ya tiene otro CUIL, el servicio MUST NOT modificar su CUIL ni registrar el consentimiento recibido, y MUST marcarlo con `requiereRevision` = true y motivo `CUIL_DISTINTO`.

#### Scenario: Mismo email, otro CUIL
- **WHEN** existe un cliente con email `martina@gmail.com` y CUIL `27-30111222-5`
- **AND** `leads` envía `POST /clientes` con ese email, CUIL `20-12345678-6` y `consentimiento` = true
- **THEN** responde 200 con el mismo `clienteId` y `coincidencia` = `EMAIL`
- **AND** la ficha muestra `cuil` = `XX-XXXX1222-X`, `requiereRevision` = true y `motivoRevision` = `CUIL_DISTINTO`
- **AND** el cliente sigue teniendo un único consentimiento de precalificación, el original

### Requirement: Posible duplicado entre dos clientes
Cuando el CUIL informado pertenece a un cliente activo y el email informado pertenece a otro cliente activo, el servicio MUST resolver el alta como recontacto del cliente del CUIL y MUST marcarlo con `requiereRevision` = true y motivo `POSIBLE_DUPLICADO`.
MUST NOT modificar al cliente del email.

#### Scenario: CUIL de un cliente y email de otro
- **WHEN** existe el cliente A con CUIL `27-30111222-5` y email `martina@gmail.com`
- **AND** existe el cliente B sin CUIL y con email `martu.lopez@gmail.com`
- **AND** `leads` envía `POST /clientes` con CUIL `27-30111222-5`, `consentimiento` = true y email `martu.lopez@gmail.com`
- **THEN** responde 200 con el `clienteId` de A y `coincidencia` = `CUIL`
- **AND** A queda con `requiereRevision` = true y `motivoRevision` = `POSIBLE_DUPLICADO`
- **AND** B no cambia

### Requirement: Se conserva el primer motivo de revisión
Si un cliente ya está marcado para revisión, una nueva marca MUST NOT reemplazar el motivo registrado.

#### Scenario: Segunda marca sobre un cliente ya marcado
- **WHEN** un cliente tiene `requiereRevision` = true y `motivoRevision` = `CUIL_DISTINTO`
- **AND** un alta posterior lo marcaría con `POSIBLE_DUPLICADO`
- **THEN** el cliente sigue con `motivoRevision` = `CUIL_DISTINTO`
