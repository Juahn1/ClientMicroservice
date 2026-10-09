# Design: Edición, no contactar y anonimización de clientes

> Fuentes: `docs/specs-seed/clientes-service-spec.md` (§5 y §6.3) y el contrato del equipo `contratos-backend.md` (v1.1).
> Motivación y alcance: ver `proposal.md`. Requisitos: ver `specs/clientes-gestion/spec.md`, `specs/clientes-eventos/spec.md` y `specs/clientes-transversal/spec.md`.

## Context

- Los changes 01 y 02 están archivados. Ya existen:
  - `ApiExceptionHandler` (ProblemDetail con `codigo`) y `CorrelationIdFilter` (MDC `correlationId`);
  - la clave de servicio (`@RequiereClaveServicio`), solo en `POST /clientes`;
  - el modelo `Cliente`, separado de `ClienteEntity`, con el adaptador `ClienteRepositorioJpa` que traduce entre los dos.
- Hoy `Cliente` es casi inmutable: `nombre`, `apellido`, `nombreBusqueda`, `email`, `provincia`, `estado`, `version` y `anonimizadoEn` son `final`, y `nombreBusqueda` se calcula en el constructor.
- `ClienteEntity` tiene `@Version`, y `aEntidad` copia la versión del dominio antes de `saveAndFlush`. Una versión vieja produce `OptimisticLockingFailureException` sin código extra.
- El alta bloquea la fila con `SELECT ... FOR UPDATE` (`buscarActivoPorEmailParaActualizar`) y corre cada intento en su propia transacción (`TransactionTemplate`).
- `V1__cliente.sql` ya prevé la anonimización:
  - `email` es nullable, con el check `ck_cliente_activo_con_email`;
  - todos los índices son parciales sobre `estado = 'ACTIVO'`, así que el email de un anonimizado queda libre.
- La infraestructura de Kafka ya está preparada: `spring-kafka`, Testcontainers Kafka en el POM, el broker en `infra/docker-compose.yml` y los tópicos en `clientes.kafka.topicos`. Los paquetes `domain/event`, `application/listener` e `infrastructure/messaging` existen vacíos.
- No hay autenticación de usuarios en la Primera Parte (S-03).

## Goals / Non-Goals

**Goals:**
- Edición segura frente a dos operadores que editan a la vez.
- Una marca no contactar que no falle por concurrencia y que no toque la deduplicación.
- Una anonimización difícil de disparar por error y extensible por los changes siguientes.
- Eventos que no bloqueen ni reviertan la operación si Kafka falla.

**Non-Goals:**
- Outbox: los eventos se pueden perder si el proceso cae entre el commit y el envío (backlog `clientes-10`).
- Vaciar un campo opcional (teléfono, localidad) por PATCH: un null no modifica (ver D4).
- Editar `telefonoAlternativo` y `requiereRevision` → change 04.
- Auditoría persistente de quién editó: solo queda en los logs (D9).

## Decisions

### D1. Estructura de paquetes

```text
contracts
├── com.uade.crm.contracts            EventoIntegracion<T> (sobre común)
└── com.uade.crm.contracts.clientes   ClienteActualizado, ClienteAnonimizado, CampoCliente

com.uade.crm.clientes
├── api
│   ├── ClienteController             + PATCH, PUT/DELETE no-contactar, POST anonimizacion
│   └── dto                           EdicionClienteRequest, AnonimizacionRequest
├── application
│   ├── EdicionClienteService, NoContactarService, AnonimizacionClienteService
│   └── listener                      ClienteEventosListener (@TransactionalEventListener AFTER_COMMIT)
├── domain
│   ├── model                         Cliente (+ editar, marcar/quitarNoContactar, anonimizar),
│   │                                 CambiosCliente, ClienteAnonimizadoException
│   ├── event                         ClienteActualizadoEvento, ClienteAnonimizadoEvento
│   └── port                          ClienteRepositorio (+ buscarPorId, buscarPorIdParaActualizar),
│                                     PublicadorEventos
├── infrastructure/messaging          PublicadorEventosKafka, KafkaConfig (NewTopic)
└── config                            UsuarioFilter (X-Usuario → MDC)
```

Sigue la regla del seed de un servicio de aplicación por caso de uso (S-04). El dominio no conoce Kafka: publica eventos de dominio, y el listener los traduce a eventos de integración usando el puerto `PublicadorEventos`.

### D2. `Cliente` deja de ser inmutable

- Los campos editables dejan de ser `final`. `nombreBusqueda` se recalcula en un único método privado, que se llama cada vez que cambian `nombre` o `apellido`.
- Métodos de dominio. Cada uno **devuelve qué cambió**, para que la aplicación decida si guarda y si publica:

| Método | Devuelve | Regla |
| --- | --- | --- |
| `editar(CambiosCliente, ahora)` | `Set<CampoCliente>` (vacío si no hubo cambios reales) | Solo para activos. Un null no modifica. Compara contra el valor actual ya normalizado (email, teléfono), así que reenviar el mismo dato en otro formato no es un cambio. |
| `marcarNoContactar(ahora)` | `boolean` | Si ya estaba marcado, no hace nada y conserva `noContactarDesde`. |
| `quitarNoContactar(ahora)` | `boolean` | Si no estaba marcado, no hace nada. |
| `anonimizar(ahora)` | `boolean` | Si ya estaba anonimizado, devuelve false. |
| `registrarRecontacto(...)` (existente) | — | Además pone `noContactar = false` y `noContactarDesde = null`. |

- `actualizadoEn` solo cambia cuando hubo cambios. Si no hay cambios, la aplicación no llama a `guardar` y `@Version` no sube: así se cumple "sin cambios reales, el ETag no cambia".
- `CambiosCliente` es un record del dominio con los valores ya normalizados (`Email`, `Telefono`, `Provincia`). Lo arma `ClienteFactory`, que ya concentra la normalización del alta, para que la edición aplique exactamente las mismas reglas.

**Por qué devolver los cambios y no registrar eventos dentro de `Cliente`:** el patrón de Spring Data `@DomainEvents` solo se dispara sobre la entidad JPA, y acá el dominio está separado de `ClienteEntity`. Devolver el set de cambios mantiene a `Cliente` testeable sin Spring y deja la publicación en la aplicación.

### D3. Migración `V2__no_contactar.sql`

```sql
ALTER TABLE cliente
    ADD COLUMN no_contactar       BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN no_contactar_desde TIMESTAMPTZ,
    ADD CONSTRAINT ck_cliente_no_contactar_fecha
        CHECK (no_contactar = (no_contactar_desde IS NOT NULL)),
    ADD CONSTRAINT ck_cliente_anonimizado_sin_datos
        CHECK (estado <> 'ANONIMIZADO'
               OR (email IS NULL AND telefono_e164 IS NULL AND telefono_alternativo IS NULL AND localidad IS NULL));
```

- La marca es una **columna, no un estado**. Si `NO_CONTACTAR` fuera un valor de `estado`, el cliente quedaría fuera de `ux_cliente_email_activo` y de `buscarActivoPorEmail`, y su próximo alta crearía un duplicado.
- El segundo check es defensa en profundidad: si una anonimización se olvida de algún campo personal, falla en la base en lugar de dejar datos.
- **Numeración:** el seed reservaba `V2` para el CUIL (change 04). Las migraciones se numeran en el orden en que se mergean (S-05), así que el 04 pasa a `V3`. Esto se anota en el seed al archivar.
- Los índices no cambian. La búsqueda no filtra por la marca: el telemarketer tiene que ver al cliente marcado para no llamarlo.

### D4. `PATCH /clientes/{id}`

Orden de evaluación, en una sola transacción:

```text
1. falta If-Match                       -> 428 PRECONDICION_REQUERIDA   (antes de leer la base)
2. Bean Validation del cuerpo           -> 422 VALIDACION / TELEFONO_INVALIDO
3. buscarPorId (cualquier estado)       -> 404 CLIENTE_NO_ENCONTRADO | 410 CLIENTE_ANONIMIZADO
4. If-Match != version actual           -> 412 VERSION_DESACTUALIZADA
5. email nuevo de otro cliente activo   -> 409 EMAIL_EN_USO
6. cliente.editar(...) sin cambios      -> 200 con la ficha y el mismo ETag (no guarda)
7. guardar (saveAndFlush)               -> 200 con la ficha y el ETag nuevo
     OptimisticLockingFailureException   -> 412 (otra escritura entre el paso 3 y el 7)
     violación de ux_cliente_email_activo -> 409 (carrera con un alta o con otra edición)
```

- **`If-Match`:** acepta `W/"3"` y `"3"`. Cualquier otro valor, incluido `*`, se trata como "no coincide" (412): `*` anularía el control de concurrencia.
- **Cuerpo:** `EdicionClienteRequest` con todos los campos opcionales y las mismas validaciones que `AltaClienteRequest` (patrón de nombre, largo, formato de email, enum de provincia), sin `@NotBlank`. Las validaciones se extraen a constantes o anotaciones compartidas para no duplicarlas. Un string en blanco se trata como ausente, igual que en el alta. Media type: `application/json`.
- **Semántica de null:** ausente o null = no modificar (seed §6.3). La consecuencia es que no se puede vaciar teléfono ni localidad. Se acepta para el 03; si hace falta, se agrega `application/merge-patch+json` (RFC 7396) más adelante sin romper el contrato.
- **Email en uso:** se consulta antes (paso 5) para devolver un 409 claro, y el índice único sigue siendo la garantía real (paso 7). Solo se traduce a 409 la violación de `ux_cliente_email_activo`, identificada por el nombre de la constraint. Cualquier otra violación sigue siendo un 500.
- **Concurrencia con un recontacto:** el recontacto toma `FOR UPDATE` y sube la versión, así que el `UPDATE` del PATCH espera y después falla por versión. El resultado es un 412 y el front recarga la ficha. Es el comportamiento buscado: el recontacto cambió la ficha.

### D5. Marca no contactar

- `PUT /clientes/{id}/no-contactar` y `DELETE /clientes/{id}/no-contactar`, sin cuerpo, sin `If-Match`, siempre 204.
- `NoContactarService` lee el cliente con un método nuevo, `buscarPorIdParaActualizar` (`FOR UPDATE`). Así se encola detrás de un recontacto o de otra marca en lugar de fallar por `@Version`. Sin `If-Match`, un 412 no tendría sentido para el usuario.
- Si la operación cambió la marca → guardar y publicar `ClienteActualizado{[NO_CONTACTAR]}`. Si no → 204 sin escribir ni publicar.
- Si un recontacto quita la marca en paralelo, gana la última transacción. Se acepta: las dos son acciones legítimas.

**Por qué no es un campo del PATCH:** el opt-out se pide con el cliente al teléfono. Exigir `If-Match` haría fallar el pedido si otro operador editó la ficha un minuto antes, por un conflicto sobre datos que el opt-out ni siquiera toca.

### D6. Anonimización

- `POST /clientes/{id}/anonimizacion` con cuerpo `{ "email": "..." }`. El email es `@NotBlank` y se valida siempre, también en una repetición.
- Flujo en `AnonimizacionClienteService`, con lectura `FOR UPDATE`:
  1. Inexistente → 404.
  2. Ya anonimizado → 204 sin comparar el email (ya no hay email contra el cual comparar) y sin evento.
  3. `Email.de(cuerpo)` distinto del email del cliente → 422 `CONFIRMACION_INVALIDA`. La respuesta no revela el email registrado.
  4. `cliente.anonimizar(ahora)` y luego guardar:
     - nombre y apellido pasan a `"ANONIMIZADO"`, y `nombreBusqueda` se recalcula con ese valor;
     - email, teléfonos y localidad pasan a null;
     - `estado = ANONIMIZADO`, con `anonimizadoEn` y `actualizadoEn`;
     - se conservan id, provincia, canales, contador, fechas y la marca no contactar (no es dato personal).
  5. Se publica el evento de dominio `ClienteAnonimizadoEvento` y responde 204.
- **Extensión para los changes 04, 05 y del historial:** `ClienteAnonimizadoEvento` se publica con `ApplicationEventPublisher` **dentro** de la transacción. Cada módulo futuro borra lo suyo (CUIL, consentimientos, caché crediticia) con un `@TransactionalEventListener(phase = BEFORE_COMMIT)` o un `@EventListener`, en la misma transacción. Si la limpieza falla, la anonimización completa se revierte. `AnonimizacionClienteService` no necesita conocer a esos módulos (Observer).

**Por qué `POST .../anonimizacion` y no `DELETE /clientes/{id}`:** la confirmación necesita un cuerpo. RFC 9110 no le da semántica al cuerpo de un `DELETE`, algunos proxies lo descartan, y OpenAPI 3.0 no lo admite. Mandar el email en un header o en la URL lo dejaría en los logs del gateway. Además, el registro no se borra: es una acción sobre el recurso, no su eliminación.

### D7. 410 para clientes anonimizados

- Nuevo método de puerto `buscarPorId(id)` que trae el cliente en cualquier estado. `ConsultaClienteService.obtener` y los servicios de escritura distinguen entre inexistente (404) y anonimizado (`ClienteAnonimizadoException` → 410).
- El 410 es un ProblemDetail sin datos del cliente.
- La búsqueda sigue filtrando por `ACTIVO`: un anonimizado no aparece y no hace falta un 410 ahí.
- Exponer que el ID existió es aceptable: son UUID aleatorios, y `leads`, que ya tiene el `clienteId`, gana la posibilidad de distinguir "referencia rota" de "cliente suprimido".

### D8. Eventos de integración

- **Sobre común** en `contracts`: `EventoIntegracion<T>(UUID eventId, String eventType, int version, Instant occurredAt, String correlationId, T payload)`, con el formato del seed §5.5. Es un record genérico sin Spring, y lo pueden reutilizar los otros servicios.
- **Payloads** en `com.uade.crm.contracts.clientes`:
  - `ClienteActualizado(UUID clienteId, List<CampoCliente> campos)`, con los campos en el orden del enum para que la salida sea determinística;
  - `ClienteAnonimizado(UUID clienteId)`.
- **Flujo:**

```text
Service (tx) --ApplicationEventPublisher--> ClienteActualizadoEvento / ClienteAnonimizadoEvento
                                                   |
                         @TransactionalEventListener(AFTER_COMMIT)   (no se ejecuta si hay rollback)
                                                   |
                                 ClienteEventosListener -> PublicadorEventos (puerto)
                                                   |
                         PublicadorEventosKafka: KafkaTemplate.send(topico, clienteId, sobre)
```

- El evento de dominio captura el `correlationId` del MDC al crearse, así que no depende del hilo en que corra el listener.
- **Serialización:** JSON con `JsonSerializer` y `spring.json.add.type.headers=false`. Los consumidores de otros servicios no necesitan las clases Java de `clientes`. La clave del mensaje es el `clienteId` como string, lo que asegura el orden por cliente (§5.11).
- **Falla del broker:** el listener corre en el hilo del request después del commit. Para que un broker caído no demore la respuesta, se configura `max.block.ms: 2000` y `delivery.timeout.ms: 10000`. El resultado del `send` se maneja en `whenComplete`: si falla, se loguea en `ERROR` con el `clienteId` y el `eventType`, sin datos personales. Ninguna excepción del envío sale del listener.
- **Tópicos:** beans `NewTopic` (`cliente-actualizado` y `cliente-anonimizado`, 3 particiones, réplicas desde configuración, 1 en local). `KafkaAdmin` los crea al arrancar y no impide el arranque si el broker no está.
- **Alternativa descartada:** listener `@Async`. Libera el hilo, pero pierde el MDC y agrega un pool que configurar; con un `max.block.ms` corto no hace falta. El outbox, que es la solución completa, queda para el Integrador.

### D9. `X-Usuario` para trazabilidad

- Un `UsuarioFilter` toma el header opcional `X-Usuario`. Valida el formato `[A-Za-z0-9._@-]{1,60}` (un valor fuera de formato se ignora, como el `correlationId`) y lo deja en el MDC como `usuario`. El patrón de logback lo incluye.
- Los servicios de escritura loguean `clienteId`, operación y campos cambiados, nunca los valores. El usuario no viaja en los eventos.
- Es falsificable, porque no hay autenticación. Sirve como rastro operativo, no como auditoría. En el Integrador se reemplaza por el usuario del JWT.

### D10. Errores nuevos

| `codigo` | HTTP | Cuándo |
| --- | --- | --- |
| `PRECONDICION_REQUERIDA` | 428 | PATCH sin `If-Match` |
| `VERSION_DESACTUALIZADA` | 412 | `If-Match` no coincide, o conflicto optimista al guardar |
| `EMAIL_EN_USO` | 409 | PATCH con el email de otro cliente activo |
| `CONFIRMACION_INVALIDA` | 422 | Anonimización con un email que no es el del cliente |
| `CLIENTE_ANONIMIZADO` | 410 | Cualquier operación por ID sobre un cliente anonimizado (salvo repetir la anonimización) |

Se agregan a `CodigoError` y se traducen en `ApiExceptionHandler`. `CLIENTE_NO_ENCONTRADO` pasa a significar solo "nunca existió".

### D11. Configuración

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:29092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      properties:
        spring.json.add.type.headers: false
        max.block.ms: 2000
        delivery.timeout.ms: 10000
        request.timeout.ms: 5000

clientes:
  kafka:
    topicos:
      cliente-actualizado: cliente-actualizado
      cliente-anonimizado: cliente-anonimizado
    particiones: 3
    replicas: 1
```

Los tests de integración agregan un contenedor Kafka (`KafkaDePrueba`, con `@ServiceConnection`) a `@PruebaIntegracion`, como ya se hace con `PostgresDePrueba`.

## Risks / Trade-offs

- **[Sin autenticación, cualquiera que llegue al gateway puede editar, marcar o anonimizar]** → Riesgo aceptado para la Primera Parte, igual que con los `GET`, que ya exponen datos personales. Mitigaciones:
  - la anonimización exige el email del cliente como confirmación;
  - `X-Usuario` y `correlationId` quedan en los logs;
  - JWT con roles en el Integrador, sin cambios en el dominio.
- **[Evento perdido si el proceso cae entre el commit y el envío, o si el broker no responde]** → Se loguea en `ERROR` con el `clienteId`. `leads` se puede resincronizar con `GET /clientes/{id}`. Outbox en el Integrador (`clientes-10`).
- **[Un broker caído agrega hasta unos 2 s a las escrituras]** → `max.block.ms` acotado. Las lecturas no se ven afectadas.
- **[Editar el email rompe el vínculo con preformularios en vuelo]** → Si `leads` reenvía el email viejo después de la edición, se crea un cliente nuevo. Es raro (ventana corta) y se documenta para el equipo.
- **[No se puede vaciar teléfono ni localidad]** → Limitación de la semántica de null (D4). Se puede cambiar el valor, pero no borrarlo.
- **[Recontacto concurrente con un PATCH]** → El PATCH recibe 412 y el front recarga. Es correcto, pero el front tiene que manejar el 412 mostrando la ficha nueva.
- **[El front no puede leer el `ETag` si el gateway no lo expone]** → En llamadas cross-origin, el gateway tiene que incluir `ETag` en `Access-Control-Expose-Headers` y permitir `If-Match` y `X-Usuario` en `Access-Control-Allow-Headers`. Se coordina junto con la tarea pendiente del gateway (change 01, 3.3).
- **[`ANONIMIZADO` en nombre y apellido podría confundirse con datos reales]** → El estado y el 410 lo distinguen. Nunca se devuelve en una respuesta.
- **[Que `leads` no consuma los eventos]** → El servicio cumple publicándolos. El efecto en la cola depende de `leads`, y queda como acuerdo pendiente en `proposal.md`, Impact.

## Migration Plan

- Flyway aplica `V2__no_contactar.sql` al arrancar. Los clientes existentes quedan con `no_contactar = false`. Ningún cliente está anonimizado todavía, así que el nuevo check no falla.
- Los tópicos se crean solos al arrancar, si el broker está disponible. Si no, se crean en el siguiente arranque, o los crea el broker al primer envío si tiene la autocreación habilitada.
- Antes de integrar: avisar a `leads` (consumo de tópicos y 410), al front (endpoints nuevos, `If-Match`, 412 y 428) y al gateway (CORS de `ETag`), y actualizar `contratos-backend.md`.
- Rollback: revertir el PR. `V2` solo agrega columnas con default y constraints, así que la versión anterior del código funciona con la base migrada (Hibernate `validate` ignora las columnas extra).

## Open Questions

- Cantidad de particiones definitiva de los tópicos: se arranca con 3 y es configurable, sin impacto en el código.
- Si `leads` necesita `NO_CONTACTAR` con un tópico propio o le alcanza con filtrar `cliente-actualizado`. Hoy se publica en `cliente-actualizado`, y separarlo después es solo configuración y un consumidor más.
