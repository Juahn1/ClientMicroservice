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

#### Scenario: Recontacto con datos distintos
- **WHEN** existe un cliente "Martina López" de BUENOS_AIRES, sin localidad, con teléfono `+5491112345678`, canal de origen INSTAGRAM y 1 contacto
- **AND** se envía `POST /clientes` con el mismo email, nombre "Martu", apellido "Gómez", provincia CORDOBA, localidad "Lanús", teléfono `351 412-3456` y canal WHATSAPP
- **THEN** el cliente conserva nombre "Martina", apellido "López", provincia BUENOS_AIRES y teléfono `+5491112345678`
- **AND** queda con localidad "Lanús", `telefonoAlternativo` = `+5493514123456`, `canalOrigen` INSTAGRAM, `ultimoCanal` WHATSAPP y `contactosRegistrados` = 2

#### Scenario: Recontacto que completa el teléfono
- **WHEN** existe un cliente sin teléfono
- **AND** se envía `POST /clientes` con su email y el teléfono `11 1234-5678`
- **THEN** el cliente queda con teléfono `+5491112345678` y `telefonoAlternativo` en null

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
El servicio MUST devolver la ficha de un cliente activo por su ID, con el header `ETag` igual a su versión.
Si el cliente no existe o no está activo, MUST responder 404 con `codigo` = `CLIENTE_NO_ENCONTRADO`.

#### Scenario: Cliente existente
- **WHEN** se consulta `GET /clientes/{id}` de un cliente activo
- **THEN** responde 200 con nombre, apellido, email, teléfono, provincia, localidad, canales, cantidad de contactos y fecha del último contacto
- **AND** el header `ETag` contiene la versión actual del cliente

#### Scenario: La versión cambia con un recontacto
- **WHEN** un cliente con `ETag` `W/"0"` recibe un recontacto
- **THEN** `GET /clientes/{id}` devuelve un `ETag` distinto de `W/"0"`

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
- **AND** cada elemento de `contenido` es un resumen del cliente

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
