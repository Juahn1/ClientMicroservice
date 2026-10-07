## ADDED Requirements

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
