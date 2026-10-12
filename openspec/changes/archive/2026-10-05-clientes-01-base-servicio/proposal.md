# Proposal: Base del servicio clientes

## Why
Antes de agregar funcionalidad, el servicio necesita una base común: módulo propio, base de datos,
formato de errores, trazabilidad por correlationId y protección de datos personales en logs.
Todos los changes siguientes la reutilizan.

## What Changes
- Nuevo módulo `clientes-service` en el monorepo (Java 21, Spring Boot 3), puerto interno 8082, base `clientes_db`.
- Manejo global de errores con ProblemDetail (RFC 7807) y campo `codigo`.
- Filtro de correlationId propagado a logs, respuestas, llamadas salientes y eventos.
- Enmascarado de CUIL y teléfono en logs.
- OpenAPI con Swagger UI y endpoint de salud.
- Contenedor en docker-compose y ruta `/api/clientes/**` en el gateway.

## Impact
- Specs: `clientes-transversal` (nueva).
- Código: `clientes-service/`, `infra/docker-compose.yml`, configuración del gateway (coordinar con su responsable).
