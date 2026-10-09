# clientes-gestion Specification

## Purpose

Registro y consulta de los clientes de la agencia: alta deduplicada por email que conserva los datos existentes, ficha del cliente con su versión y búsqueda paginada de clientes activos.

## Requirements

### Requirement: Alta de cliente
El servicio MUST registrar un cliente a partir de nombre, apellido, email, provincia y, opcionalmente, teléfono, localidad y canal de origen.
Al crear un cliente nuevo MUST responder 201 con su identificador y el header `Location`.

#### Scenario: Cliente nuevo
- **WHEN** `leads` envía `POST /clientes` con un email que no pertenece a ningún cliente activo
- **THEN** responde 201 con `clienteId`, `nuevo` = true y `coincidencia` = `NINGUNA`
- **AND** incluye el header `Location` con la ruta del cliente
- **AND** el cliente queda con `contactosRegistrados` = 1 y `ultimoCanal` igual a `canalOrigen`

#### Scenario: Cliente nuevo sin teléfono ni canal
- **WHEN** `leads` envía `POST /clientes` sin `telefono` ni `canalOrigen`
- **THEN** responde 201
- **AND** la ficha del cliente tiene `telefono`, `canalOrigen` y `ultimoCanal` en null

### Requirement: Deduplicación por email
El servicio MUST considerar el mismo cliente a dos altas cuyo email coincida después de recortar espacios y pasar a minúsculas.
Si ya existe un cliente activo con ese email, MUST NOT crear otro y MUST responder 200 con el cliente existente y el header `Location`.

#### Scenario: Mismo email con otro formato
- **WHEN** existe un cliente activo con email `martina@gmail.com`
- **AND** se envía `POST /clientes` con email ` Martina@Gmail.com `
- **THEN** responde 200 con el `clienteId` existente, `nuevo` = false y `coincidencia` = `EMAIL`
- **AND** incluye el header `Location` con la ruta del cliente existente

#### Scenario: Altas simultáneas con el mismo email
- **WHEN** llegan dos `POST /clientes` concurrentes con el mismo email
- **THEN** existe un único cliente activo con ese email
- **AND** ambas respuestas devuelven el mismo `clienteId`
- **AND** el cliente queda con `contactosRegistrados` = 2

### Requirement: El recontacto no sobrescribe datos existentes
Cuando el alta coincide con un cliente existente, el servicio MUST NOT modificar su nombre, apellido ni provincia.
MUST completar solo los campos vacíos, sumar 1 a `contactosRegistrados` y actualizar `ultimoContactoEn` y, si se informa canal, `ultimoCanal`.
Si el cliente tenía la marca no contactar, el recontacto MUST quitarla: la persona volvió a pedir contacto.

#### Scenario: Recontacto con datos distintos
- **WHEN** existe un cliente "Martina López" de BUENOS_AIRES, sin localidad, con teléfono `+5491112345678`, canal de origen INSTAGRAM y 1 contacto
- **AND** se envía `POST /clientes` con el mismo email, nombre "Martu", apellido "Gómez", provincia CORDOBA, localidad "Lanús", teléfono `351 412-3456` y canal WHATSAPP
- **THEN** el cliente conserva nombre "Martina", apellido "López", provincia BUENOS_AIRES y teléfono `+5491112345678`
- **AND** queda con localidad "Lanús", `telefonoAlternativo` = `+5493514123456`, `canalOrigen` INSTAGRAM, `ultimoCanal` WHATSAPP y `contactosRegistrados` = 2

#### Scenario: Recontacto que completa el teléfono
- **WHEN** existe un cliente sin teléfono
- **AND** se envía `POST /clientes` con su email y el teléfono `11 1234-5678`
- **THEN** el cliente queda con teléfono `+5491112345678` y `telefonoAlternativo` en null

#### Scenario: Recontacto de un cliente marcado como no contactar
- **WHEN** existe un cliente con `noContactar` = true
- **AND** `leads` envía `POST /clientes` con su email
- **THEN** responde 200 con el mismo `clienteId`
- **AND** la ficha muestra `noContactar` = false y `noContactarDesde` en null

### Requirement: Normalización del teléfono
Cuando se informa un teléfono, el servicio MUST normalizarlo al formato E.164 de móvil argentino (`+549` seguido de característica y número)
y MUST rechazar con 422 y `codigo` = `TELEFONO_INVALIDO` los números que no se puedan normalizar.

#### Scenario: Número sin el 9 de móvil
- **WHEN** se envía el teléfono `11 1234-5678`
- **THEN** se guarda como `+5491112345678`

#### Scenario: Número con 0 y 15
- **WHEN** se envía el teléfono `0351 15 412-3456`
- **THEN** se guarda como `+5493514123456`

#### Scenario: Número incompleto
- **WHEN** se envía el teléfono `1512345678`
- **THEN** responde 422 con `codigo` = `TELEFONO_INVALIDO` y un error en el campo `telefono`

### Requirement: Validación de los datos de alta
El servicio MUST exigir nombre y apellido de 2 a 120 caracteres (letras, espacios, apóstrofo o guion), email con formato válido y provincia del enum `Provincia`,
y MUST validar que el canal de origen pertenezca al enum `CanalOrigen` cuando se informa. Cada incumplimiento MUST responder 422 con `codigo` = `VALIDACION`.

#### Scenario: Provincia inexistente
- **WHEN** se envía `POST /clientes` con provincia "BS AS"
- **THEN** responde 422 con `codigo` = `VALIDACION` y un error en el campo `provincia`

#### Scenario: Email ausente
- **WHEN** se envía `POST /clientes` sin `email`
- **THEN** responde 422 con `codigo` = `VALIDACION` y un error en el campo `email`

#### Scenario: Apellido con caracteres inválidos
- **WHEN** se envía `POST /clientes` con apellido "L0pez"
- **THEN** responde 422 con `codigo` = `VALIDACION` y un error en el campo `apellido`

### Requirement: Consulta de la ficha del cliente
El servicio MUST devolver la ficha de un cliente activo por su ID, con el header `ETag` igual a su versión y la marca no contactar.
Si el cliente no existe, MUST responder 404 con `codigo` = `CLIENTE_NO_ENCONTRADO`; si fue anonimizado, MUST responder 410 con `codigo` = `CLIENTE_ANONIMIZADO`.

#### Scenario: Cliente existente
- **WHEN** se consulta `GET /clientes/{id}` de un cliente activo
- **THEN** responde 200 con nombre, apellido, email, teléfono, provincia, localidad, canales, cantidad de contactos, fecha del último contacto, `noContactar` y `noContactarDesde`
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

### Requirement: Búsqueda paginada de clientes
El servicio MUST permitir buscar clientes activos por teléfono y por email (coincidencia exacta después de normalizar), por nombre y apellido y por provincia, combinando los filtros con AND.
Sin filtros MUST devolver todos los clientes activos. El orden MUST ser `ultimoContactoEn` descendente.

#### Scenario: Búsqueda por teléfono en formato libre
- **WHEN** se consulta `GET /clientes?telefono=11 1234 5678`
- **THEN** devuelve el cliente cuyo teléfono normalizado es `+5491112345678`

#### Scenario: Búsqueda por email
- **WHEN** se consulta `GET /clientes?email=MARTINA@gmail.com`
- **THEN** devuelve el cliente con email `martina@gmail.com`

#### Scenario: Filtros combinados
- **WHEN** existen "Martina López" de BUENOS_AIRES y "Martín López" de CORDOBA
- **AND** se consulta `GET /clientes?q=lopez&provincia=CORDOBA`
- **THEN** devuelve solo a "Martín López"

#### Scenario: Teléfono inválido en la búsqueda
- **WHEN** se consulta `GET /clientes?telefono=123`
- **THEN** responde 422 con `codigo` = `TELEFONO_INVALIDO`

#### Scenario: Forma de la respuesta
- **WHEN** se consulta `GET /clientes`
- **THEN** la respuesta contiene `contenido`, `pagina`, `tamanio`, `totalElementos` y `totalPaginas`
- **AND** cada elemento de `contenido` es un resumen del cliente que incluye `noContactar`

### Requirement: Búsqueda por nombre sin distinguir mayúsculas ni acentos
El filtro `q` MUST encontrar clientes cuyo nombre y apellido contengan el texto buscado, sin distinguir mayúsculas, acentos ni la diferencia entre ñ y n.
Los caracteres `%` y `_` de `q` MUST tratarse como texto literal.

#### Scenario: Búsqueda sin acentos
- **WHEN** existe la clienta "Martina López"
- **AND** se consulta `GET /clientes?q=lopez`
- **THEN** la respuesta incluye a "Martina López"

#### Scenario: Búsqueda sobre nombre y apellido
- **WHEN** existe la clienta "Martina López"
- **AND** se consulta `GET /clientes?q=TINA LOP`
- **THEN** la respuesta incluye a "Martina López"

#### Scenario: Comodines como texto literal
- **WHEN** existen clientes activos y ninguno tiene `%` en el nombre
- **AND** se consulta `GET /clientes?q=%`
- **THEN** la respuesta tiene `contenido` vacío

### Requirement: Tamaño de página limitado
La búsqueda MUST usar 20 elementos por página por defecto y MUST devolver como máximo 50, aunque se pida un tamaño mayor.

#### Scenario: Tamaño de página excedido
- **WHEN** se consulta `GET /clientes?size=500`
- **THEN** la página devuelta tiene como máximo 50 elementos y `tamanio` = 50

#### Scenario: Tamaño por defecto
- **WHEN** se consulta `GET /clientes` sin `size`
- **THEN** `tamanio` = 20

### Requirement: Edición con control de concurrencia
El servicio MUST permitir modificar nombre, apellido, email, teléfono, provincia y localidad de un cliente activo mediante `PATCH /clientes/{id}`, exigiendo el header `If-Match` con la versión vigente.
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

### Requirement: Email único al editar
Al editar, el servicio MUST rechazar un email que, normalizado, pertenezca a otro cliente activo, respondiendo 409 con `codigo` = `EMAIL_EN_USO` sin modificar al cliente.

#### Scenario: Email de otro cliente
- **WHEN** existe otro cliente activo con email `diego@gmail.com`
- **AND** se envía `PATCH /clientes/{id}` con email ` Diego@Gmail.com `
- **THEN** responde 409 con `codigo` = `EMAIL_EN_USO`
- **AND** el cliente conserva su email

#### Scenario: Email nuevo usado en un alta posterior
- **WHEN** se edita el email de un cliente a `martina.lopez@gmail.com`
- **AND** luego `leads` envía `POST /clientes` con `martina.lopez@gmail.com`
- **THEN** responde 200 con el `clienteId` de ese cliente

### Requirement: Marca no contactar
El servicio MUST permitir marcar a un cliente activo como no contactable con `PUT /clientes/{id}/no-contactar` y quitar la marca con `DELETE /clientes/{id}/no-contactar`, sin `If-Match`.
Ambas operaciones MUST ser idempotentes y responder 204. La marca MUST NOT cambiar el estado del cliente ni eliminar ninguno de sus datos.

#### Scenario: Marcar como no contactar
- **WHEN** se envía `PUT /clientes/{id}/no-contactar` de un cliente activo sin la marca
- **THEN** responde 204
- **AND** la ficha muestra `noContactar` = true y `noContactarDesde` con la fecha de la operación
- **AND** el cliente conserva todos sus datos y sigue apareciendo en la búsqueda

#### Scenario: Marcar dos veces
- **WHEN** se envía `PUT /clientes/{id}/no-contactar` de un cliente que ya tiene la marca
- **THEN** responde 204 y `noContactarDesde` conserva la fecha original

#### Scenario: Quitar la marca
- **WHEN** se envía `DELETE /clientes/{id}/no-contactar` de un cliente marcado
- **THEN** responde 204
- **AND** la ficha muestra `noContactar` = false y `noContactarDesde` en null

#### Scenario: Quitar una marca inexistente
- **WHEN** se envía `DELETE /clientes/{id}/no-contactar` de un cliente sin la marca
- **THEN** responde 204 y el cliente no cambia

#### Scenario: Marca sobre un cliente anonimizado
- **WHEN** se envía `PUT /clientes/{id}/no-contactar` de un cliente anonimizado
- **THEN** responde 410 con `codigo` = `CLIENTE_ANONIMIZADO`

#### Scenario: Marca sobre un cliente inexistente
- **WHEN** se envía `PUT /clientes/{id}/no-contactar` con un ID que nunca existió
- **THEN** responde 404 con `codigo` = `CLIENTE_NO_ENCONTRADO`

### Requirement: Anonimización a pedido del titular
El servicio MUST atender `POST /clientes/{id}/anonimizacion` eliminando los datos personales del cliente (nombre y apellido reemplazados por `ANONIMIZADO`; email, teléfonos y localidad eliminados) y conservando el ID, la provincia, los canales, el contador de contactos y las fechas.
La operación MUST responder 204, MUST ser idempotente y MUST liberar el email para un alta futura.

#### Scenario: Anonimización
- **WHEN** se envía `POST /clientes/{id}/anonimizacion` de un cliente activo con su email como confirmación
- **THEN** responde 204
- **AND** `GET /clientes/{id}` responde 410 con `codigo` = `CLIENTE_ANONIMIZADO`
- **AND** el cliente no aparece en la búsqueda

#### Scenario: Datos conservados y eliminados
- **WHEN** se anonimiza un cliente con nombre, apellido, email, teléfono, teléfono alternativo y localidad
- **THEN** su registro conserva el ID, la provincia, el canal de origen, el contador de contactos y las fechas
- **AND** no conserva su nombre, apellido, email, teléfonos ni localidad

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

### Requirement: Confirmación de la anonimización
Para anonimizar un cliente activo, el cuerpo del request MUST incluir su email, que se compara después de recortar espacios y pasar a minúsculas.
Si no coincide, el servicio MUST responder 422 con `codigo` = `CONFIRMACION_INVALIDA` y MUST NOT modificar al cliente.

#### Scenario: Confirmación con otro formato
- **WHEN** el cliente tiene email `martina@gmail.com`
- **AND** se envía `POST /clientes/{id}/anonimizacion` con email ` MARTINA@gmail.com `
- **THEN** responde 204 y el cliente queda anonimizado

#### Scenario: Confirmación incorrecta
- **WHEN** se envía `POST /clientes/{id}/anonimizacion` con un email distinto del registrado
- **THEN** responde 422 con `codigo` = `CONFIRMACION_INVALIDA`
- **AND** el cliente sigue activo y sin cambios

#### Scenario: Confirmación ausente
- **WHEN** se envía `POST /clientes/{id}/anonimizacion` de un cliente activo sin `email` en el cuerpo
- **THEN** responde 422 con `codigo` = `VALIDACION` y un error en el campo `email`
