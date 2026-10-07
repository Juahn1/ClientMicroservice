# clientes-transversal Specification

## Purpose

Comportamientos transversales del servicio clientes que reutilizan todos sus endpoints: formato uniforme de errores (ProblemDetail con `codigo`), trazabilidad por `correlationId`, protección del CUIL y del teléfono en los logs, y publicación del contrato OpenAPI.

## Requirements

### Requirement: Formato de errores
El servicio MUST responder todo error de su API REST con el formato ProblemDetail (RFC 7807),
media type `application/problem+json`, incluyendo un campo `codigo` estable y el `correlationId` del request.

#### Scenario: Recurso inexistente
- **WHEN** se consulta `GET /clientes/{id}` con un id que no existe
- **THEN** responde 404 con `codigo` = `CLIENTE_NO_ENCONTRADO`
- **AND** el cuerpo incluye `correlationId`

#### Scenario: Datos inválidos
- **WHEN** un request tiene campos que no cumplen las validaciones
- **THEN** responde 422 con `codigo` = `VALIDACION`
- **AND** incluye una lista `errores` con `campo` y `mensaje` por cada problema

#### Scenario: Cuerpo mal formado
- **WHEN** un request tiene un JSON mal formado
- **THEN** responde 400 con `codigo` = `JSON_INVALIDO`

### Requirement: Propagación del correlationId
El servicio MUST tomar el header `X-Correlation-Id` de cada request, o generar un UUID si no viene,
y MUST incluirlo en todos los logs del request, devolverlo en el header de la respuesta
y propagarlo en las llamadas HTTP salientes y en los eventos publicados.

#### Scenario: Header presente
- **WHEN** llega un request con `X-Correlation-Id: abc-123`
- **THEN** la respuesta incluye el header `X-Correlation-Id: abc-123`
- **AND** los logs de ese request contienen `abc-123`

#### Scenario: Header ausente
- **WHEN** llega un request sin `X-Correlation-Id`
- **THEN** el servicio genera un UUID y lo devuelve en el header de la respuesta

### Requirement: Datos personales fuera de los logs
El servicio MUST NOT escribir en logs el CUIL completo ni el número de teléfono completo;
cuando necesite referenciarlos MUST usar su forma enmascarada.

#### Scenario: Log de una operación con datos personales
- **WHEN** se procesa un request que contiene el CUIL `20-12345678-6` y el teléfono `11 1234-5678`
- **THEN** ninguna línea de log contiene `20123456786`, `20-12345678-6` ni `1112345678`

### Requirement: Documentación de la API
El servicio MUST publicar su contrato OpenAPI en `/v3/api-docs` y Swagger UI en `/swagger-ui.html`.

#### Scenario: Contrato disponible
- **WHEN** se consulta `GET /v3/api-docs`
- **THEN** responde 200 con un documento OpenAPI 3 que describe los endpoints del servicio

### Requirement: Acceso restringido a servicios autorizados
Los endpoints internos de escritura (en este change, `POST /clientes`) MUST exigir el header `X-Api-Key` con la clave configurada del servicio autorizado.
Si falta o no coincide, MUST responder 401 con `codigo` = `CLAVE_SERVICIO_INVALIDA` antes de validar el cuerpo. La clave MUST NOT aparecer en los logs.

#### Scenario: Alta sin clave
- **WHEN** se envía `POST /clientes` sin el header `X-Api-Key`
- **THEN** responde 401 con `codigo` = `CLAVE_SERVICIO_INVALIDA`
- **AND** no se crea ningún cliente

#### Scenario: Alta con clave incorrecta y cuerpo inválido
- **WHEN** se envía `POST /clientes` con una `X-Api-Key` incorrecta y un cuerpo sin `email`
- **THEN** responde 401 con `codigo` = `CLAVE_SERVICIO_INVALIDA`, no 422

#### Scenario: Alta con la clave de leads
- **WHEN** se envía `POST /clientes` con la `X-Api-Key` configurada y un cuerpo válido
- **THEN** el alta se procesa normalmente

#### Scenario: Consultas sin clave
- **WHEN** se consulta `GET /clientes/{id}` o `GET /clientes` sin `X-Api-Key`
- **THEN** la consulta se procesa normalmente

#### Scenario: La clave no aparece en logs
- **WHEN** se procesa un `POST /clientes` con una `X-Api-Key`
- **THEN** ninguna línea de log contiene el valor de la clave
