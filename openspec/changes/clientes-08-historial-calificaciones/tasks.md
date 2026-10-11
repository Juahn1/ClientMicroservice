# Tasks

## 1. Configuración

- [x] 1.1 Agregar a `ClientesProperties` `Kafka.Topicos.leadCalificado` y `leadCalificadoDlt` (y actualizar el Javadoc de `Kafka`) y el record `Historial` con `zonaFecha` y `Reintentos` (design D9). Cargar los valores en `application.yml` y los cortos de reintentos en `application-test.yml` (3 intentos desde 50 ms). Verificar ampliando `ContextoTest` con los valores cargados y con un `ApplicationContextRunner`, o un test de binding, que una `zona-fecha` inválida impide el arranque
- [x] 1.2 Agregar Awaitility como dependencia de test en `clientes-service/pom.xml` (versión gestionada por Spring Boot). Verificar que `mvn -pl clientes-service test-compile` pasa

## 2. Dominio y persistencia

- [x] 2.1 Crear `CalificacionHistorica` (record, `motivos` inmutable, `toString()` sin `motivos` ni `observacion`) y el puerto `HistorialCalificaciones` (design D4). Verificar con un test unitario que `toString()` no contiene los textos de motivos ni de observación y que `motivos` no se puede modificar
- [x] 2.2 Escribir `V5__historial_calificacion.sql` con la tabla, la clave única, el check de `motivos` y el índice del design D4. Verificar que `ContextoTest` arranca con `ddl-auto: validate` y, con un test de repositorio, que la base rechaza `motivos` que no sea un array
- [x] 2.3 Implementar `HistorialCalificacionEntity` (`motivos` con `@JdbcTypeCode(SqlTypes.JSON)`), `HistorialCalificacionJpaRepository` (con el `INSERT ... SELECT ... FOR SHARE ON CONFLICT DO NOTHING` nativo) y `HistorialCalificacionesJpa` (`registrar`, `delCliente`, `eliminarDelCliente`) (design D4). Verificar con tests de repositorio sobre Testcontainers:
  - registrar inserta y devuelve true;
  - el mismo registro dos veces deja una fila y la segunda devuelve false;
  - un cliente anonimizado o inexistente devuelve false sin insertar;
  - `delCliente` ordena por `calculada_en` descendente y devuelve los motivos en orden;
  - `eliminarDelCliente` borra solo las filas de ese cliente;
  - dos registros concurrentes del mismo evento terminan sin error con una sola fila

## 3. Registro de calificaciones

- [x] 3.1 Implementar `LeadCalificadoTraductor` y `MensajeInvalidoException` (design D2 y D3). Verificar con tests unitarios:
  - evento plano y sobre común (incluido `eventType` ajeno → inválido);
  - cada campo obligatorio faltante o con tipo incorrecto;
  - límites de `nivel`, `origen` y `observacion`;
  - `nivel` desconocido aceptado y `puntos` negativos;
  - `motivos` ausente, `null` y con un elemento no textual;
  - fecha sin zona (`2026-10-11T15:30:00` → `2026-10-11T18:30:00Z`), con `Z` y con offset, y nanosegundos truncados a microsegundos;
  - un campo extra ignorado;
  - el mensaje de la excepción nombra el campo pero no contiene su valor
- [x] 3.2 Implementar `RegistroCalificacionService` (`@Transactional`, `registradaEn` del `Clock`, clasificación del descarte con log WARN, INFO o DEBUG) (design D6). Verificar con tests unitarios, con dobles del puerto y del repositorio de clientes, los cuatro caminos, sus niveles de log y que la clasificación solo consulta el repositorio cuando no se insertó
- [x] 3.3 Implementar `LeadCalificadoListener` (MDC con `correlationId` del sobre o generado, y `leadId`; `finally` que limpia el MDC) y exponer como método estático el chequeo de formato de `CorrelationIdFilter`. Implementar `ConsumidorKafkaConfig` (`DefaultErrorHandler` con `ExponentialBackOffWithMaxRetries` desde `clientes.historial.reintentos`, `MensajeInvalidoException` no reintentable, `DeadLetterPublishingRecoverer` con partición -1 y un productor `String`/`byte[]` propio que no es bean y se cierra al destruir el contexto). Sumar a `KafkaConfig` el `NewTopic` del DLT y a `application.yml` la configuración del consumidor (design D5, D7). Verificar que `ContextoTest` sigue verde (el `KafkaTemplate` de eventos sigue existiendo) y que `CorrelationIdFilterTest` sigue verde
- [x] 3.4 Crear la `@TestConfiguration` que crea `lead-calificado` para `@PruebaIntegracion` y un helper de test que publica bytes y lee el DLT filtrando por un texto único (design D10). Verificar con un test de integración nuevo los escenarios de:
  - "Registro de las calificaciones publicadas";
  - "Formato del evento de calificación";
  - "Fecha de cálculo";
  - "Registro idempotente";
  - "Solo se registran calificaciones de clientes activos" (cliente anonimizado, inexistente con su log WARN, y anonimización simultánea con dos hilos);
  - "Mensajes que no se pueden procesar" (ilegible con el contenido original en el DLT, campo faltante, falla transitoria que se recupera y falla persistente, ambas con el trigger y la secuencia de design D10);
  - "Datos financieros fuera de los logs", con `OutputCaptureExtension` en DEBUG

## 4. Consulta y anonimización

- [x] 4.1 Implementar `HistorialCalificacionesService`, `HistorialController` (`GET /clientes/{id}/historial`, sin clave de servicio) y los DTOs `HistorialResponse` y `CalificacionResponse`, con documentación OpenAPI (valores de ejemplo de `nivel` y `origen`, respuestas 200, 404 y 410) (design D8). Verificar con un test de integración los escenarios de "Consulta del historial de calificaciones", insertando las filas por el puerto, y con `GET /v3/api-docs` que el contrato incluye `/clientes/{id}/historial`
- [x] 4.2 Implementar `HistorialAnonimizacionListener` (`BEFORE_COMMIT`, `eliminarDelCliente`) (design D6). Verificar ampliando `AnonimizacionApiTest`:
  - el escenario "Historial de calificaciones eliminado": registrar calificaciones, anonimizar y comprobar por SQL que no queda ninguna fila del cliente;
  - una falla al eliminar, provocada con un trigger temporal, revierte la anonimización

## 5. Documentación para el equipo

- [x] 5.1 Escribir `docs/contrato-equipo/clientes-08-para-contratos-backend.md`:
  - para el front: endpoint, forma de la respuesta con un ejemplo, orden, 404 y 410, sin paginar y sin clave;
  - para `calificacion`: qué consume `clientes` (tópico, grupo `clientes-historial`, campos obligatorios, zona asumida para `fecha`, DLT);
  - sugerencias que no bloquean: `fecha` con zona, sobre común, usuario de la corrección y sufijo de puntos duplicado en `motivos`;
  - la dependencia de punta a punta con `leads`;
  - cerrar los "[A confirmar]" del historial del contrato.

  Verificar que el texto cubre cada punto de "Impact" del proposal sobre `calificacion` y el front
- [x] 5.2 Actualizar `docs/specs-seed/clientes-service-spec.md`:
  - §6: fila del change 08 con la capability `clientes-historial-calificaciones`;
  - §8: la reactivación por stock pasa de `clientes-08` a `clientes-16`, con la nota de que su consumidor puede necesitar la tabla de `eventId` procesados;
  - §5.5: agregar los tópicos que consume `clientes`.

  Documentar el endpoint y el consumidor en el `README`. Verificar que las secciones mencionadas reflejan el alcance del proposal

## 6. Verificación integral

- [x] 6.1 Con `docker compose up`, verificar a mano:
  - crear `lead-calificado` con `kafka-topics.sh`;
  - dar de alta un cliente con la clave de `leads`;
  - publicar con `kafka-console-producer.sh` el ejemplo del responsable de `calificacion` con ese `clienteId`;
  - `GET /clientes/{id}/historial` devuelve la calificación con `calculadaEn` en UTC;
  - publicar un mensaje que no es JSON y verificar que aparece en `lead-calificado.clientes.DLT` con `kafka-console-consumer.sh`.

  Después, correr `mvn verify` y `openspec validate clientes-08-historial-calificaciones --strict` y verificar que los dos pasan sin errores
