# Design: Base del servicio clientes

> Fuente: `docs/specs-seed/clientes-service-spec.md`, secciones 1 y 5, filtradas a lo que aplica a este change.
> Motivación y alcance: ver `proposal.md`. Requisitos: ver `specs/clientes-transversal/spec.md`.

## Context

- El repo no tiene código todavía: solo `docs/`, `openspec/` y `.claude/`. No hay POM padre, ni módulo `contracts`, ni gateway, ni `infra/docker-compose.yml`. Este repo pasa a ser la raíz Maven multi-módulo (D0).
- Stack fijado por el documento (§1): Java 21, Spring Boot 3, Maven multi-módulo, PostgreSQL 16 con Spring Data JPA y Flyway, Kafka con Spring for Apache Kafka, springdoc-openapi, Resilience4j, libphonenumber. Tests con JUnit 5, Mockito, AssertJ y Testcontainers. Docker y Docker Compose para el laboratorio.
- Reglas no negociables (§1): capas `api → application → domain ← infrastructure`; errores REST en ProblemDetail (RFC 7807) con `codigo`; configuración externalizada por variables de entorno y perfiles; CUIL nunca en logs.
- Paquete base `com.uade.crm.clientes`, puerto interno 8082, base `clientes_db`.
- Los changes siguientes (02 a 07) reutilizan todo lo de este.

## Goals / Non-Goals

**Goals:**
- Módulo `clientes-service` que arranca contra PostgreSQL y Kafka (Testcontainers en tests, docker-compose en laboratorio).
- Infraestructura transversal: errores, correlationId, enmascarado en logs, OpenAPI, salud.
- Estructura de paquetes completa de §5.1 (vacía donde todavía no hay código) y `ClientesProperties` con toda la configuración de §5.9.

**Non-Goals:**
- Tablas: la primera migración (`V1__cliente.sql`) llega en el change 02.
- Endpoints de negocio, cifrado del CUIL (04), productores Kafka (03), adaptadores de situación crediticia (05).
- Autenticación (Integrador; S-03).

## Decisions

### D0. Estructura del repo y versiones

Este repo es la raíz Maven multi-módulo:

```text
ClientMicroservice/
├── pom.xml            padre (packaging pom), groupId com.uade.crm, versión 0.1.0-SNAPSHOT
├── clientes-service/  este change
├── contracts/         se crea en el change 02 (enums Provincia y CanalOrigen)
├── infra/             docker-compose.yml
├── docs/
└── openspec/
```

- Spring Boot: la última versión 3.x estable al momento de implementar, usada como parent/BOM. springdoc-openapi en la versión compatible con esa línea de Boot; Resilience4j y libphonenumber en su última estable.
- Alternativa descartada: proyecto único sin POM padre, porque §1 fija Maven multi-módulo y el change 02 necesita `contracts`.

### D1. Estructura de paquetes (§5.1)

```text
com.uade.crm.clientes
├── api
│   ├── dto
│   └── error          ApiExceptionHandler (@RestControllerAdvice → ProblemDetail)
├── application
│   └── listener
├── domain
│   ├── model, valor, event, port
├── infrastructure
│   ├── persistence, crypto, precalificacion, messaging
└── config             ClientesProperties, Resilience4j, Kafka, OpenAPI, CorrelationIdFilter
```

En este change solo se llenan `api.error` y `config`; el resto se crea para que los changes siguientes respeten las capas.

### D2. Formato de errores (§5.4, ejemplo de §5.3)

- `ApiExceptionHandler` (`@RestControllerAdvice`) devuelve `ProblemDetail` con media type `application/problem+json`.
- Propiedades extendidas: `codigo` (estable) y `correlationId` (tomado del MDC); `instance` = ruta del request.
- `type` = base configurable `https://crm.uade.local/errores/` + `codigo` en kebab-case (`CLIENTE_NO_ENCONTRADO` → `cliente-no-encontrado`), generalizando el ejemplo de §5.3.
- Errores que cubre este change:

| Caso | `codigo` | HTTP |
| --- | --- | --- |
| Falla de Bean Validation, con lista `errores` (`campo`, `mensaje`) | `VALIDACION` | 422 |
| Cuerpo mal formado | `JSON_INVALIDO` | 400 |
| Recurso inexistente | `CLIENTE_NO_ENCONTRADO` | 404 |

- Spring responde 400 por defecto ante `MethodArgumentNotValidException`; hay que sobrescribirlo para responder 422 como pide el catálogo.
- El resto del catálogo de §5.4 se agrega en el change que introduce cada caso.

Alternativa descartada: formato de error propio. RFC 7807 es regla de arquitectura y Spring Boot 3 lo soporta nativamente.

### D3. Propagación del correlationId

- `CorrelationIdFilter` (servlet filter de máxima prioridad): lee `X-Correlation-Id` o genera un UUID, lo pone en el MDC, lo devuelve en el header de la respuesta y limpia el MDC al terminar.
- Patrón de logback que incluye el `correlationId` del MDC.
- **Postergado:** el interceptor de `RestClient` y el header `X-Correlation-Id` en los mensajes Kafka. En este change no hay llamadas salientes ni productores, así que el requisito de propagación no tiene a qué aplicarse todavía. Se agregan con el primer productor Kafka (change 03) y con el primer cliente HTTP saliente (adaptador SOAP, backlog clientes-09).
- Alternativa descartada: implementarlos ahora sin uso real (código que se prueba solo en forma aislada).

### D4. Datos personales fuera de los logs (§5.6, §5.8)

- Utilidad `Enmascarador`: CUIL → `XX-XXXX` + últimos 4 dígitos del DNI + `-X` (ej. `XX-XXXX5678-X`); teléfono → `+54911****5678`.
- Ningún `toString()` de entidad o DTO incluye CUIL ni teléfono completos (regla para los changes siguientes).
- Converter de logback que enmascara, en cualquier línea (incluidos los logs de Spring e Hibernate), los CUIL (11 dígitos, con o sin guiones) y los teléfonos, usando `Enmascarador`. El código también usa `Enmascarador` explícitamente cuando necesita referenciar el dato.
- Alternativa descartada: solo `Enmascarador` en el código; no protege contra logs de frameworks que imprimen parámetros.
- Test automático con `OutputCaptureExtension` que verifica que la salida de logs no contiene el CUIL ni el teléfono.

### D5. Configuración (§5.9)

- `ClientesProperties` (`@ConfigurationProperties(prefix = "clientes")`) con todo el árbol de §5.9: teléfono, CUIL (claves desde `CLIENTES_CUIL_CLAVE` y `CLIENTES_CUIL_HMAC`), situación crediticia, interesados, paginación y tópicos Kafka.
- `application.yml` con perfiles `local` y `docker`; `server.port: 8082`.
- Claves del CUIL: el perfil `local` y los tests traen claves ficticias **fijas** (para que los `cuil_hash` no cambien entre arranques, porque la clave HMAC no se puede rotar sin recalcular todos los hashes). En el perfil `docker` son obligatorias y el arranque falla si `CLIENTES_CUIL_CLAVE` o `CLIENTES_CUIL_HMAC` no están definidas.
- Configuración de Resilience4j de §5.9 (`situacionCrediticia`) declarada desde ahora.

### D6. OpenAPI y salud

- springdoc: contrato en `/v3/api-docs`, Swagger UI en `/swagger-ui.html`, con título, versión y descripción del servicio.
- Actuator: `/actuator/health`, usado por el healthcheck de docker-compose.

### D7. Flyway y base

- Flyway apunta a `clientes_db`; en este change no hay migraciones (la `V1` llega en el 02). Una migración por change; nunca se edita una ya mergeada (S-05).
- Cada servicio es dueño de su base; ningún otro servicio lee `clientes_db` (§1).

### D8. Contenedor y despliegue

- Dockerfile multi-stage: build con Maven, runtime JRE 21.
- `infra/docker-compose.yml` en este repo con:
  - PostgreSQL 16 con un script de inicialización que crea `clientes_db`;
  - Kafka en modo KRaft;
  - el servicio `clientes`: puerto 8082, publicado al host (`8082:8082`) para usar Swagger UI y Postman mientras el gateway no esté integrado; healthcheck con actuator; variables `CLIENTES_CUIL_CLAVE` y `CLIENTES_CUIL_HMAC`.
- La ruta `/api/clientes/**` en el gateway queda fuera de este repo: se coordina con su responsable (task 3.3).

### D9. Tests

- Arranque de contexto con Testcontainers (PostgreSQL y Kafka).
- Escenarios de errores (404, 422, 400), correlationId con y sin header, y logs sin datos personales.
- Como los endpoints de negocio llegan en los changes 02 y 04, los escenarios de errores y de logs se prueban con un controller **solo de test** (en `src/test`) que lanza las excepciones y recibe un cuerpo con validaciones, CUIL y teléfono. Así el 01 cierra su spec completo sin código de negocio.

### D10. Ajustes surgidos durante la implementación

- **Códigos fuera del catálogo §5.4.** El spec exige `codigo` en *todo* error, así que se agregan `SOLICITUD_INVALIDA` (4xx que resuelve Spring MVC solo: 405, 415, ruta inexistente) y `ERROR_INTERNO` (500, sin detalle hacia afuera).
- **Formato de `X-Correlation-Id`.** Un valor que no cumpla `[A-Za-z0-9._-]{1,64}` se trata como ausente y se genera un UUID, para evitar inyección en logs y headers.
- **`clientes.errores.tipo-base`.** Propiedad nueva (no está en §5.9) para la base configurable del `type` (D2).
- **Patrón de logback.** `%nopex` va al principio y `%n%ex` dentro de `%mask(...)`, porque logback no reconoce una conversión pegada al `)` de un composite y la imprimía como texto.
- **Validación de las claves del CUIL.** Spring Boot liga un placeholder sin resolver (`${CLIENTES_CUIL_CLAVE}`) como texto literal, que pasa `@NotBlank`: el servicio arrancaba sin claves. Se valida que sean base64, la de cifrado de exactamente 32 bytes (AES-256) y la de hash de al menos 32. Verificado con la imagen real: sin claves, el arranque falla con un mensaje que no expone el valor.
- **Infra local.** Kafka se publica al host en `29092` (listener `EXTERNO`). Los puertos del host son configurables (`POSTGRES_PUERTO_HOST`, `KAFKA_PUERTO_HOST`, `CLIENTES_PUERTO_HOST`) porque pueden estar ocupados por otros proyectos. Credenciales de la base por `CLIENTES_DB_USUARIO`/`CLIENTES_DB_CLAVE`; el script de init crea `clientes_db` con su propio dueño. La imagen corre con un usuario sin privilegios.
- **Locale fijo `es`** para que los mensajes de Bean Validation salgan en español sin depender del sistema operativo.

## Risks / Trade-offs

- [El enmascarado por regex también tapa números que no son datos personales] → cualquier secuencia de 8 o más dígitos con forma de teléfono (por ejemplo, una fecha `2026-10-05` suelta o un importe) se enmascara en los logs. Las fechas ISO con hora y los UUID no se tocan (cubierto por tests).

- [El controller de test no prueba los endpoints reales] → los changes 02 y 04 repiten los escenarios de errores y de logs con sus endpoints.
- [Claves ficticias en `local` versionadas en el repo] → solo sirven para datos de prueba; en `docker` son obligatorias y nunca se versionan claves reales.
- [Propagación del correlationId postergada] → el seed del change 03 pide el `correlationId` en el sobre del evento pero no como header Kafka; hay que agregarlo al proponer el 03.
- [Ruta del gateway fuera de este repo] → la task 3.3 depende de otro integrante y puede quedar abierta sin bloquear el resto.
- [Publicación en Kafka AFTER_COMMIT sin outbox] → aceptado para la Primera Parte; el outbox queda para el Integrador (§1, backlog clientes-10).
- [Logback como única barrera contra datos personales] → se complementa con la regla de `toString()` y con el test de captura de logs.

## Migration Plan

- Módulo nuevo; no hay datos ni versiones anteriores.
- Rollback: revertir el PR. La ruta del gateway se coordina con su responsable, así que su cambio puede ir en otro PR.

## Open Questions

Las 9 preguntas abiertas originales se resolvieron con el usuario el 2026-10-05:

| # | Pregunta | Decisión |
| --- | --- | --- |
| 1 | Monorepo vs. este repo | POM padre en este repo, con módulos `clientes-service` y `contracts` (D0). |
| 2 | Gateway y docker-compose | Compose en este repo con PostgreSQL, Kafka y `clientes`; gateway afuera (D8). |
| 3 | Endpoints para los escenarios | Controller solo de test (D9). |
| 4 | Versiones | Última Spring Boot 3.x estable, groupId `com.uade.crm`, `0.1.0-SNAPSHOT` (D0). |
| 5 | correlationId en Kafka y llamadas salientes | Postergado al primer productor/cliente (D3). |
| 6 | Enmascarado en logback | Converter de logback más `Enmascarador` (D4). |
| 7 | Claves del CUIL | Ficticias fijas en `local` y tests; obligatorias en `docker` (D5). |
| 8 | Puerto publicado | `8082:8082` al host (D8). |
| 9 | `type` de ProblemDetail | Derivado del `codigo` en kebab-case (D2). |
