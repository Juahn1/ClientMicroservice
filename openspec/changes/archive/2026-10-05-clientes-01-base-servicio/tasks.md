# Tasks

## 1. Estructura del módulo
- [x] 1.1 Crear `clientes-service` en el POM padre con dependencias: web, validation, data-jpa, flyway, postgresql, spring-kafka, actuator, springdoc-openapi, resilience4j, libphonenumber; test: testcontainers (postgresql, kafka), assertj
- [x] 1.2 Crear la estructura de paquetes de la sección 5.1 del documento semilla
- [x] 1.3 `application.yml` con perfiles `local` y `docker`; `ClientesProperties` con `@ConfigurationProperties` (sección 5.9)
- [x] 1.4 Configurar Flyway apuntando a `clientes_db` (las tablas llegan en los changes siguientes)

## 2. Transversal
- [x] 2.1 `ApiExceptionHandler` con ProblemDetail, campo `codigo` y `correlationId`
- [x] 2.2 `CorrelationIdFilter` + MDC (el interceptor para RestClient y el header en mensajes Kafka se postergan al primer cliente/productor; ver design D3)
- [x] 2.3 Utilidad `Enmascarador` para CUIL y teléfono; patrón de logback sin datos personales
- [x] 2.4 springdoc con título, versión y descripción del servicio

## 3. Infraestructura
- [x] 3.1 Dockerfile multi-stage (build con Maven, runtime JRE 21)
- [x] 3.2 Servicio `clientes` en docker-compose: puerto interno 8082, healthcheck con actuator, variables `CLIENTES_CUIL_CLAVE` y `CLIENTES_CUIL_HMAC`
- [ ] 3.3 Ruta `/api/clientes/**` en el gateway (coordinar con el responsable) — **PENDIENTE al archivar (2026-10-05):** el gateway real está fuera de este repo. Se continúa después del change 02 con un gateway simulado en docker-compose y un generador de datos mock para probar la ingesta por `/api/clientes/**`; falta definir en qué change, con qué tecnología (nginx o Spring Cloud Gateway) y el modo del generador. Requisitos a cubrir: `StripPrefix` de `/api`, propagar `X-Correlation-Id` y no loguear cuerpos de requests (AC-07).

## 4. Tests
- [x] 4.1 Test de arranque de contexto con Testcontainers
- [x] 4.2 Tests de los escenarios de errores (404, 422, 400)
- [x] 4.3 Test de correlationId con y sin header
- [x] 4.4 Test de logs con `OutputCaptureExtension` (escenario de datos personales)
