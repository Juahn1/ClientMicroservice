# Tasks

## 1. Contracts, configuración y errores

- [x] 1.1 Agregar a `contracts` el sobre `EventoIntegracion<T>` (`com.uade.crm.contracts`) y `ClienteActualizado`, `ClienteAnonimizado` y `CampoCliente` (`com.uade.crm.contracts.clientes`), sin dependencias de Spring (design D8); verificar con un test unitario de `contracts` que `CampoCliente` tiene los 7 valores y que `mvn -q verify` compila
- [x] 1.2 Configurar el productor Kafka (`spring.kafka.*` del design D11: serializadores, `acks`, sin type headers, `max.block.ms`, `delivery.timeout.ms`), `clientes.kafka.particiones` y `clientes.kafka.replicas` en `ClientesProperties`, `application.yml`, `application-test.yml` y docker-compose; agregar `KafkaDePrueba` (Testcontainers, `@ServiceConnection`) a `@PruebaIntegracion`; verificar que `ContextoTest` arranca con Postgres y Kafka
- [x] 1.3 Agregar `PRECONDICION_REQUERIDA` (428), `VERSION_DESACTUALIZADA` (412), `EMAIL_EN_USO` (409), `CONFIRMACION_INVALIDA` (422) y `CLIENTE_ANONIMIZADO` (410) a `CodigoError`, con sus excepciones de dominio o aplicación traducidas en `ApiExceptionHandler` (design D10); verificar ampliando `ErroresApiTest` con un caso por código que confirme status, `codigo` y `correlationId`
- [x] 1.4 Implementar `UsuarioFilter` (`X-Usuario` opcional, formato validado, MDC `usuario`) e incluir `usuario` en el patrón de `logback-spring.xml` (design D9); verificar con un test que un request con `X-Usuario: tm-ana` deja `tm-ana` en los logs y que un valor fuera de formato se ignora

## 2. Persistencia

- [x] 2.1 Escribir `V2__no_contactar.sql` con las columnas y los dos checks del design D3, y mapear `noContactar` y `noContactarDesde` en `ClienteEntity` y en el adaptador; verificar que `ContextoTest` (`ddl-auto: validate`) arranca y, con un test de repositorio, que la base rechaza un anonimizado con email
- [x] 2.2 Agregar al puerto `ClienteRepositorio` `buscarPorId` (cualquier estado) y `buscarPorIdParaActualizar` (`FOR UPDATE`), y exponer en el adaptador si una `DataIntegrityViolationException` viene de `ux_cliente_email_activo`; verificar con tests de repositorio sobre Testcontainers que devuelven clientes anonimizados y que la violación del email se identifica por nombre

## 3. Dominio

- [x] 3.1 Hacer mutables los campos editables de `Cliente`, recalcular `nombreBusqueda` al cambiar nombre o apellido, e implementar `editar(CambiosCliente, ahora)` que devuelve el `Set<CampoCliente>` modificado; agregar a `ClienteFactory` la construcción normalizada de `CambiosCliente` (design D2); verificar con tests unitarios: cambio de apellido, null no modifica, el mismo email o teléfono en otro formato no cuenta como cambio y la edición de un no activo falla
- [x] 3.2 Implementar `marcarNoContactar` y `quitarNoContactar` (idempotentes, conservan la fecha original) y hacer que `registrarRecontacto` quite la marca (design D2); verificar con tests unitarios y ampliando `ClienteRecontactoTest` con el escenario "Recontacto de un cliente marcado como no contactar"
- [x] 3.3 Implementar `anonimizar(ahora)` según el design D6, paso 4 (qué se anula y qué se conserva; devuelve false si ya estaba anonimizado); verificar con tests unitarios que cubren "Datos conservados y eliminados" y la repetición
- [x] 3.4 Crear los eventos de dominio `ClienteActualizadoEvento` (clienteId, campos, correlationId capturado del MDC) y `ClienteAnonimizadoEvento` en `domain/event`, y el puerto `PublicadorEventos`; verificar con un test unitario que el evento toma el `correlationId` presente en el MDC al crearse

## 4. Consulta con 410

- [x] 4.1 Cambiar `ConsultaClienteService.obtener` para que use `buscarPorId` y distinga 404 de 410 (design D7); agregar `noContactar` y `noContactarDesde` a `ClienteResponse` y `noContactar` a `ClienteResumenResponse`; verificar ampliando `FichaClienteApiTest` y `BusquedaClientesApiTest` con los escenarios "Cliente inexistente", "Cliente anonimizado" y "Forma de la respuesta", y actualizar la descripción OpenAPI de `GET /clientes/{id}`

## 5. Eventos de integración

- [x] 5.1 Implementar `ClienteEventosListener` (`@TransactionalEventListener(AFTER_COMMIT)`) que arma el `EventoIntegracion` y llama a `PublicadorEventos`, y `PublicadorEventosKafka` con `KafkaTemplate` (clave `clienteId`, `whenComplete` que loguea la falla sin datos personales) más los beans `NewTopic` (design D8); verificar con un test de integración contra Kafka de Testcontainers que un evento publicado llega al tópico con la clave, el sobre completo y el `correlationId`, y que un rollback no publica nada
- [x] 5.2 Verificar el requisito "La publicación no altera la operación" con un test que use un `PublicadorEventos` que falla (o un broker inaccesible): la operación responde con éxito, queda guardada y el log registra la falla con el `clienteId`

## 6. Edición

- [x] 6.1 Implementar `EdicionClienteService` con el orden de evaluación del design D4 (404/410, 412 por `If-Match`, 409 por email con pre-chequeo y traducción de la constraint, sin cambios → no guarda, `OptimisticLockingFailureException` → 412) y la publicación de `ClienteActualizadoEvento` cuando hay cambios; verificar con tests de integración los escenarios "Versión desactualizada", "Edición sin cambios reales", "Email de otro cliente" y "Email nuevo usado en un alta posterior"
- [x] 6.2 Implementar `PATCH /clientes/{id}` con `EdicionClienteRequest` (validaciones compartidas con el alta, blancos como ausentes), parseo de `If-Match` (`W/"n"` o `"n"`; otro valor → 412; ausente → 428 antes de leer la base), respuesta 200 con `ETag`, sin `@RequiereClaveServicio` y documentado en OpenAPI; verificar con tests de integración todos los escenarios de "Edición con control de concurrencia" y "Email único al editar", y que se publica `ClienteActualizado` con `campos` = [`NOMBRE`, `EMAIL`]
- [x] 6.3 Verificar con un test de integración la concurrencia entre un recontacto y un PATCH con el `ETag` previo: el PATCH responde 412 y el cliente conserva el recontacto

## 7. Marca no contactar

- [x] 7.1 Implementar `NoContactarService` (lectura `FOR UPDATE`, guarda y publica solo si cambió) y `PUT`/`DELETE /clientes/{id}/no-contactar` con 204, sin clave ni `If-Match`, documentados en OpenAPI (design D5); verificar con tests de integración todos los escenarios de "Marca no contactar", los de `clientes-eventos` sobre la marca (evento al marcar o desmarcar, ninguno al repetir) y que el recontacto que quita la marca no publica eventos

## 8. Anonimización

- [x] 8.1 Implementar `AnonimizacionClienteService` (lectura `FOR UPDATE`, 404, repetición 204 sin evento, confirmación por email normalizado → 422 `CONFIRMACION_INVALIDA`) que publica `ClienteAnonimizadoEvento` dentro de la transacción (design D6); verificar con tests de integración todos los escenarios de "Anonimización a pedido del titular" y "Confirmación de la anonimización", incluido "El email queda libre" con un `POST /clientes` posterior que responde 201
- [x] 8.2 Implementar `POST /clientes/{id}/anonimizacion` con `AnonimizacionRequest` (`email` obligatorio), 204, sin clave y documentado en OpenAPI; verificar con tests de integración que se publica `ClienteAnonimizado` con solo el `clienteId`, que la repetición no publica, y ampliar `DatosPersonalesEnLogsTest` para que los logs de edición y anonimización no contengan el email, el teléfono ni el nombre del cliente
- [x] 8.3 Verificar con un test de integración el escenario "Escrituras del personal sin clave" de `clientes-transversal`: `PATCH`, `PUT no-contactar` y `POST anonimizacion` sin `X-Api-Key` se procesan, mientras `POST /clientes` sin clave sigue respondiendo 401

## 9. Integración y documentación

- [x] 9.1 Documentar en el `README` los endpoints nuevos (`If-Match`, 412 y 428, `X-Usuario`, confirmación de la anonimización), los tópicos y el formato de los eventos para `leads`, y el CORS requerido en el gateway (`ETag` expuesto; `If-Match` y `X-Usuario` permitidos); preparar el texto para sumar a `contratos-backend.md`; verificar con `docker compose up` que un `PATCH` con `curl` responde 200 y que `kafka-console-consumer` muestra el `ClienteActualizado`
- [x] 9.2 Anotar en `docs/specs-seed/clientes-service-spec.md` que la migración del CUIL del change 04 pasa a `V3` (design D3); correr la suite completa (`mvn verify`) y `openspec validate clientes-03-edicion-y-baja --strict`, y verificar que los dos pasan sin errores
