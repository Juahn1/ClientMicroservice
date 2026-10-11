# clientes-situacion-crediticia Specification

## Purpose

Situación crediticia de un cliente para `calificacion`: estado explícito y número de 1 a 6 obtenidos de una fuente externa con el CUIL protegido, con caché de vigencia configurable y degradación controlada cuando la fuente no responde.

## Requirements

### Requirement: Consulta de la situación crediticia
El servicio MUST responder `GET /clientes/{id}/situacion-crediticia` de un cliente activo con 200, `clienteId` y un `estado`: `CON_DATOS`, `SIN_REGISTROS`, `SIN_CUIL` o `NO_DISPONIBLE`. Solo con `CON_DATOS` MUST incluir una `situacion` de 1 a 6.
Con `CON_DATOS` o `SIN_REGISTROS` MUST incluir `periodo` (AAAAMM), `fuente` y `consultadaEn`; con los demás estados esos campos MUST ser nulos.

#### Scenario: Consulta con datos
- **WHEN** `calificacion` consulta la situación de un cliente con CUIL `27-30111222-5`, usando el adaptador simulado
- **THEN** responde 200 con `estado` = `CON_DATOS`, `situacion` = 1, `fuente` = `SIMULADA`, `periodo` y `consultadaEn`

#### Scenario: Sin registros en la fuente
- **WHEN** `calificacion` consulta la situación de un cliente con CUIL `20-28555449-8`, usando el adaptador simulado
- **THEN** responde 200 con `estado` = `SIN_REGISTROS`, `situacion` nula, `fuente` = `SIMULADA` y `periodo` y `consultadaEn` informados

#### Scenario: Cliente sin CUIL
- **WHEN** `calificacion` consulta la situación de un cliente que no tiene CUIL
- **THEN** responde 200 con `estado` = `SIN_CUIL` y `situacion`, `periodo`, `fuente` y `consultadaEn` nulos
- **AND** la fuente no se consulta

### Requirement: Situación de un cliente inexistente o anonimizado
Si el cliente no existe, la consulta MUST responder 404 con `codigo` = `CLIENTE_NO_ENCONTRADO`; si fue anonimizado, MUST responder 410 con `codigo` = `CLIENTE_ANONIMIZADO`. En ninguno de los dos casos MUST consultar la fuente.

#### Scenario: Cliente inexistente
- **WHEN** `calificacion` consulta la situación con un ID que nunca existió
- **THEN** responde 404 con `codigo` = `CLIENTE_NO_ENCONTRADO`

#### Scenario: Cliente anonimizado
- **WHEN** `calificacion` consulta la situación de un cliente anonimizado
- **THEN** responde 410 con `codigo` = `CLIENTE_ANONIMIZADO`
- **AND** la fuente no se consulta

### Requirement: El CUIL no sale del servicio en la consulta
La respuesta de la situación crediticia MUST NOT incluir el CUIL en ninguna forma, ni siquiera enmascarado. El CUIL MUST usarse en claro solo para consultar la fuente y MUST NOT aparecer en los logs de la consulta.

#### Scenario: Respuesta sin CUIL
- **WHEN** `calificacion` consulta la situación de un cliente con CUIL `20-12345678-6`
- **THEN** el cuerpo de la respuesta no tiene un campo `cuil`
- **AND** no contiene `20123456786` ni `20-12345678-6`

#### Scenario: Logs sin CUIL
- **WHEN** se consulta la situación de un cliente con CUIL `20-12345678-6` con los logs en nivel DEBUG, consultando la fuente
- **THEN** ninguna línea de log contiene `20123456786` ni `20-12345678-6`

### Requirement: Caché con vigencia
El servicio MUST guardar los resultados `CON_DATOS` y `SIN_REGISTROS` de cada cliente durante la vigencia configurada (por defecto 30 días, contados desde `consultadaEn`) y MUST responder desde ese resultado sin consultar la fuente mientras esté vigente. MUST NOT guardar `NO_DISPONIBLE`.

#### Scenario: Resultado vigente
- **WHEN** se consulta dos veces la situación del mismo cliente dentro de la vigencia
- **THEN** la fuente se consulta una sola vez
- **AND** ambas respuestas son iguales, incluido `consultadaEn`

#### Scenario: Resultado vencido
- **WHEN** se consulta la situación de un cliente cuyo resultado guardado superó la vigencia
- **THEN** se vuelve a consultar la fuente
- **AND** la respuesta y el resultado guardado tienen el nuevo `consultadaEn`

#### Scenario: Un resultado no disponible no se guarda
- **WHEN** una consulta termina en `NO_DISPONIBLE` y, con la fuente restablecida y el circuito cerrado, se vuelve a consultar
- **THEN** la segunda consulta consulta la fuente y responde con su resultado

#### Scenario: Consultas simultáneas sin resultado guardado
- **WHEN** llegan a la vez dos consultas de la situación del mismo cliente, que no tiene resultado guardado
- **THEN** ambas responden 200 con el mismo `estado` y `situacion`
- **AND** queda guardado un solo resultado para ese cliente

### Requirement: Degradación ante fallas de la fuente
Si la fuente falla, no responde dentro del timeout configurado (por defecto 2 s) o el circuit breaker está abierto, la consulta MUST responder 200 con `estado` = `NO_DISPONIBLE` y MUST NOT responder un error 5xx por esa causa.
Con el circuito abierto MUST NOT consultar la fuente hasta que pase la ventana de espera configurada.

#### Scenario: Fuente caída
- **WHEN** el adaptador simulado está configurado con `forzar-no-disponible` = true
- **THEN** la consulta responde 200 con `estado` = `NO_DISPONIBLE` y `situacion` nula

#### Scenario: Fuente lenta
- **WHEN** el adaptador simulado tiene una demora mayor que el timeout
- **THEN** la consulta responde 200 con `estado` = `NO_DISPONIBLE` en un tiempo cercano al timeout, no a la demora

#### Scenario: Circuito abierto
- **WHEN** las fallas de la fuente superan el umbral configurado y se abre el circuito
- **THEN** las consultas siguientes responden 200 con `estado` = `NO_DISPONIBLE` sin consultar la fuente

#### Scenario: Reintento después de la ventana de espera
- **WHEN** el circuito está abierto, la fuente se restablece y pasa la ventana de espera configurada
- **THEN** una consulta posterior vuelve a consultar la fuente y responde con su resultado

### Requirement: Adaptador simulado determinístico
El adaptador simulado MUST resolver la situación según el último dígito del DNI contenido en el CUIL: 0 a 4 → situación 1; 5 o 6 → situación 2; 7 → situación 3; 8 → situación 5; 9 → sin registros. MUST informar como `periodo` el mes anterior al de la consulta.

#### Scenario: Situación negativa
- **WHEN** se consulta con el adaptador simulado un cliente con CUIL `20-12345678-6`
- **THEN** el resultado es `CON_DATOS` con `situacion` = 5

#### Scenario: Tabla completa
- **WHEN** se consulta con el adaptador simulado con los CUIL `27-38765430-0`, `20-41000006-8` y `20-35000007-1`
- **THEN** los resultados son `CON_DATOS` con `situacion` 1, 2 y 3 respectivamente

#### Scenario: Período informado
- **WHEN** se consulta con el adaptador simulado el 2026-10-10
- **THEN** el `periodo` es `202609`

### Requirement: Selección de la fuente por configuración
El servicio MUST elegir la fuente de la situación crediticia según `clientes.situacion-crediticia.modo`. Si el modo configurado no tiene una fuente disponible, el servicio MUST NOT arrancar.

#### Scenario: Modo simulado
- **WHEN** el servicio arranca con `modo` = `simulado`
- **THEN** las consultas usan el adaptador simulado y responden `fuente` = `SIMULADA`

#### Scenario: Modo sin fuente disponible
- **WHEN** el servicio arranca con `modo` = `bcra` antes de que exista ese adaptador
- **THEN** el arranque falla con un mensaje que nombra `clientes.situacion-crediticia.modo`
