# Design: Historial de calificaciones

> Fuentes:
> - contrato del equipo `contratos-backend.md` (v1.1), secciones de `clientes` y `calificacion`);
> - la respuesta del responsable de `calificacion` del 2026-10-11 sobre `LeadCalificadoEvent` (tópico `lead-calificado`, clave `leadId`, JSON plano);
> - seed `docs/specs-seed/clientes-service-spec.md` (§1, §5.5, §8).
>
> Motivación y alcance: ver `proposal.md`. Requisitos: ver `specs/clientes-historial-calificaciones/spec.md` y `specs/clientes-gestion/spec.md`.

## Context

- Los changes 01 a 05 están archivados. Kafka hoy solo se usa para publicar:
  - `KafkaConfig` personaliza el `ProducerFactory` de Spring Boot con un `JsonSerializer` sin headers de tipo y crea los tópicos `cliente-actualizado` y `cliente-anonimizado`;
  - `application.yml` configura solo `spring.kafka.producer`.
- Spring Boot 3.5 y spring-kafka. El starter crea un único `KafkaTemplate` y un único `ProducerFactory`, ambos con `@ConditionalOnMissingBean`. Además conecta a la fábrica de contenedores de listeners un bean `CommonErrorHandler`, si existe.
- Hay piezas del 05 que este change repite:
  - `CacheSituacionCrediticiaJpa` hace un `INSERT ... SELECT ... FROM cliente WHERE estado = 'ACTIVO' FOR SHARE ON CONFLICT`, que ordena el guardado con una anonimización concurrente (la anonimización toma la fila con `FOR UPDATE`);
  - `SituacionCrediticiaAnonimizacionListener` borra datos fuera del agregado en `BEFORE_COMMIT`.
- `ConsultaClienteService.obtener` ya resuelve 404 (`ClienteNoEncontradoException`) y 410 (`ClienteAnonimizadoException`).
- `CorrelationIdFilter` pone el `correlationId` en el MDC solo para requests HTTP. Valida su formato con un patrón privado.
- Tests:
  - `@PruebaIntegracion` comparte un único contexto con Postgres y Kafka reales (`KafkaDePrueba`, imagen `apache/kafka:3.9.1`);
  - el 05 probó un rollback con un trigger temporal de Postgres;
  - Awaitility no está en el `pom`.
- `ddl-auto: validate`: las columnas de texto van como `VARCHAR`/`TEXT`, y `jsonb` se valida contra `SqlTypes.JSON`.
- La migración que sigue es la V5.

## Goals / Non-Goals

**Goals:**
- Consumir el evento tal como lo publica hoy `calificacion`, sin pedirle cambios para funcionar.
- Que ningún mensaje, malo o con falla persistente, frene la partición, y que ninguno se pierda en silencio.
- Que la relectura del tópico, por reinicio, rebalanceo o un offset reseteado, sea inofensiva.
- Que el dominio y la aplicación no conozcan Kafka.

**Non-Goals:**
- **Reprocesar el DLT.** Es manual: se republica en `lead-calificado` con las herramientas de Kafka. Gracias a la idempotencia, eso no duplica nada.
- **Exactly-once** o transacciones de Kafka. La idempotencia por clave natural ya da el mismo resultado observable.
- **Tabla de `eventId` procesados.** Vuelve a evaluarse con `clientes-16` (reactivación), cuyos eventos no tienen clave natural.
- **Paginación, filtro por lead y marca de vigente** en `GET /historial`.

## Decisions

### D1. Estructura de paquetes

```text
com.uade.crm.clientes
├── api
│   ├── HistorialController            GET /clientes/{id}/historial (sin @RequiereClaveServicio)
│   └── dto                            HistorialResponse, CalificacionResponse
├── application
│   ├── RegistroCalificacionService    registra; si no inserta, clasifica el descarte y lo loguea
│   ├── HistorialCalificacionesService 404/410 y lista del cliente
│   └── listener                       HistorialAnonimizacionListener (BEFORE_COMMIT)
├── domain
│   ├── model                          CalificacionHistorica
│   └── port                           HistorialCalificaciones
├── infrastructure
│   ├── messaging                      LeadCalificadoListener (@KafkaListener), LeadCalificadoTraductor,
│   │                                  MensajeInvalidoException, ConsumidorKafkaConfig (error handler, DLT)
│   └── persistence                    HistorialCalificacionEntity, HistorialCalificacionJpaRepository,
│                                      HistorialCalificacionesJpa
└── config                             ClientesProperties (+ historial, + tópicos consumidos)
```

`contracts` no cambia. El evento es de `calificacion` y no hay una clase compartida: su `LeadCalificadoEvent` vive en su repo. `clientes` traduce JSON, no tipos Java.

### D2. Lectura del mensaje: bytes y traducción propia

- **Consumidor:** `key-deserializer: StringDeserializer` y `value-deserializer: ByteArrayDeserializer`. La deserialización nunca falla, así que no hace falta `ErrorHandlingDeserializer`. El mensaje ilegible se detecta en el código propio, donde se le puede dar un error claro.
- **`LeadCalificadoTraductor`** (infraestructura) convierte los bytes en un `CalificacionHistorica` con el `ObjectMapper` de la aplicación, leyendo un `JsonNode`:
  - si la raíz tiene `payload` objeto y `eventType` texto, es el **sobre común**. Exige `eventType` = `LeadCalificado` y lee los campos de `payload`; si el sobre trae `correlationId`, lo devuelve para el MDC (D7);
  - si no, lee los campos de la raíz;
  - los campos desconocidos se ignoran.
- **Validación** (cualquier incumplimiento lanza `MensajeInvalidoException`, que no se reintenta):

  | Campo | Regla |
  |---|---|
  | `leadId`, `clienteId` | UUID válido, obligatorio |
  | `puntos` | entero, obligatorio (acepta negativos) |
  | `nivel`, `origen` | texto no vacío, hasta 40 caracteres, se guarda tal cual (sin enum) |
  | `fecha` | obligatoria; ver D3 |
  | `observacion` | opcional, hasta 2000 caracteres |
  | `motivos` | opcional; si viene, array de textos; ausente o `null` → lista vacía |

- El mensaje de `MensajeInvalidoException` nombra **el campo y la regla**, nunca el valor: viaja como header al DLT y se loguea.
- **Por qué no `JsonDeserializer` con una clase destino:** sin headers de tipo hay que fijar la clase por configuración, la validación igual queda aparte, y un JSON roto obliga a sumar `ErrorHandlingDeserializer` para que no se trabe la partición. Con bytes, además, el DLT guarda el mensaje original byte a byte (D5).

### D3. Fecha de cálculo

- `ClientesProperties.Historial.zonaFecha` (`ZoneId`, por defecto `America/Argentina/Buenos_Aires`). Un id inválido impide el arranque.
- El traductor intenta en orden:
  1. `OffsetDateTime.parse` (acepta `Z` y `±hh:mm`): se respeta;
  2. `LocalDateTime.parse`: se ubica en `zonaFecha` con `atZone(...)`. En el salto de horario de verano, `atZone` elige el offset anterior; Argentina no tiene horario de verano desde 2009.
  3. Si ninguno funciona, `MensajeInvalidoException("fecha")`.
- `calculadaEn` se trunca a microsegundos, la precisión de `TIMESTAMPTZ`. Así el valor guardado y el que se compara en `ON CONFLICT` son el mismo aunque `calificacion` mande nanosegundos (el 05 hizo lo mismo con `consultadaEn`).

### D4. Modelo, tabla e idempotencia

```sql
-- V5__historial_calificacion.sql
CREATE TABLE historial_calificacion (
    id             UUID         PRIMARY KEY,
    cliente_id     UUID         NOT NULL REFERENCES cliente (id),
    lead_id        UUID         NOT NULL,              -- de leads: sin FK
    nivel          VARCHAR(40)  NOT NULL,              -- sin CHECK: vocabulario de calificacion
    puntaje        INTEGER      NOT NULL,
    origen         VARCHAR(40)  NOT NULL,              -- sin CHECK
    calculada_en   TIMESTAMPTZ  NOT NULL,
    observacion    VARCHAR(2000),
    motivos        JSONB        NOT NULL DEFAULT '[]',
    registrada_en  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_historial_calificacion UNIQUE (lead_id, origen, calculada_en),
    CONSTRAINT ck_historial_motivos CHECK (jsonb_typeof(motivos) = 'array')
);
CREATE INDEX ix_historial_cliente ON historial_calificacion (cliente_id, calculada_en DESC);
```

- `CalificacionHistorica` (record del dominio): `leadId`, `clienteId`, `nivel`, `puntaje`, `origen`, `calculadaEn`, `observacion`, `motivos` (`List<String>` inmutable). `nivel` y `origen` son `String`: el dominio no interpreta el puntaje, solo lo guarda.
- **Puerto `HistorialCalificaciones`:**
  - `boolean registrar(CalificacionHistorica, Instant registradaEn)`: true si insertó;
  - `List<CalificacionHistorica> delCliente(UUID clienteId)`: ordena por `calculada_en DESC, registrada_en DESC, id`;
  - `void eliminarDelCliente(UUID clienteId)`.
- **`registrar`** hace un `INSERT ... SELECT` nativo en una sola sentencia:

  ```sql
  INSERT INTO historial_calificacion (id, cliente_id, lead_id, nivel, puntaje, origen, calculada_en,
                                      observacion, motivos, registrada_en)
  SELECT :id, c.id, :leadId, :nivel, :puntaje, :origen, :calculadaEn, :observacion,
         CAST(:motivos AS jsonb), :registradaEn
    FROM cliente c
   WHERE c.id = :clienteId AND c.estado = 'ACTIVO'
     FOR SHARE
  ON CONFLICT (lead_id, origen, calculada_en) DO NOTHING
  ```

  Con 0 filas, el evento era un duplicado, de un cliente anonimizado o de uno desconocido. `FOR SHARE` cierra la carrera con la anonimización igual que en el 05: si la anonimización tiene la fila, el insert espera, ve `ANONIMIZADO` y no inserta; si el insert llegó antes, la anonimización espera su commit y su `DELETE` lo encuentra (D6). `motivos` se serializa a texto JSON con el `ObjectMapper`.
- **Por qué la clave natural `(lead_id, origen, calculada_en)`:** el evento no trae `eventId`. Un mensaje repetido tiene los tres valores iguales. Dos calificaciones distintas del mismo lead se diferencian en el origen o en la fecha. Si `calificacion` adopta el sobre, se sigue usando esta clave y no el `eventId`: una misma calificación republicada con otro `eventId` sigue siendo un duplicado.
- La entidad JPA sirve para leer y borrar. `motivos` se mapea con `@JdbcTypeCode(SqlTypes.JSON)` como `List<String>`.

### D5. Errores del consumidor: reintentos y DLT

Un bean `CommonErrorHandler`, que Spring Boot conecta solo a la fábrica de listeners: un `DefaultErrorHandler` con un `DeadLetterPublishingRecoverer`.

```text
excepción en el listener
   |
   +-- MensajeInvalidoException ------------------------------+  (addNotRetryableExceptions)
   |                                                          |
   +-- cualquier otra (base caída, timeout) --> reintento      |
          espera = esperaInicial * multiplicador^n            |
          hasta "intentos" reintentos                         |
          |                                                   |
          v sigue fallando                                    v
   DeadLetterPublishingRecoverer --> lead-calificado.clientes.DLT --> commit del offset --> siguiente mensaje
```

- **Reintentos:** `ExponentialBackOffWithMaxRetries(intentos)` desde `clientes.historial.reintentos.*`. Por defecto: `intentos: 5`, `espera-inicial: 2s`, `multiplicador: 2`, que da unos 62 s en total. Muy por debajo de `max.poll.interval.ms` (5 min), así que reintentar no provoca un rebalanceo. En el perfil de test: 3 intentos desde 50 ms.
- **Tópico del DLT:** `lead-calificado.clientes.DLT`, configurable. Lleva el sufijo del consumidor porque el tópico de origen es de otro servicio. Lo crea `KafkaConfig` como los tópicos propios.
- **Partición del DLT:** el recoverer por defecto escribe en la misma partición que el mensaje original, y no sabemos cuántas particiones tiene `lead-calificado`. Por eso el destino es `new TopicPartition(dlt, -1)`, que deja elegir a Kafka.
- **Productor del DLT, que no es un bean:** el recoverer necesita un `KafkaTemplate<String, byte[]>` con `StringSerializer` y `ByteArraySerializer`, para copiar el mensaje original sin pasarlo por el `JsonSerializer` del productor de eventos (que lo escaparía como texto JSON).
  - Se arma dentro del método que crea el error handler, con `KafkaProperties.buildProducerProperties(null)` y los serializers explícitos, **sin exponerlo como bean**. Si fuera un bean `KafkaTemplate` o `ProducerFactory`, el `@ConditionalOnMissingBean` de Spring Boot dejaría de crear el template del publicador de eventos.
  - Como no lo maneja Spring, el bean de configuración lo cierra al destruirse el contexto (`DisposableBean`).
- Spring agrega solo los headers del DLT (`kafka_dlt-exception-fqcn`, `-message`, tópico, partición y offset de origen). El mensaje de la excepción no lleva valores del evento (D2). Una excepción de base podría incluir un parámetro de la sentencia. Es un riesgo menor porque el DLT es interno y el mensaje original ya contiene esos datos.
- Si el broker no acepta el envío al DLT, el recoverer lanza una excepción, el offset no se confirma y el mensaje se vuelve a procesar. No se pierde.
- **Los descartes de negocio no son errores** (cliente anonimizado, desconocido o duplicado). El servicio termina normalmente y el offset se confirma (D6).

### D6. Registro y clasificación del descarte

`RegistroCalificacionService.registrar(CalificacionHistorica)` es `@Transactional`:

1. `historial.registrar(calificacion, reloj.instant())`. Si devuelve true, log INFO "Calificación registrada" con `leadId` y `clienteId`.
2. Si devuelve false, consulta `ClienteRepositorio.buscarPorId(clienteId)` solo para clasificar el descarte y loguearlo:

   | Situación | Nivel | Por qué |
   |---|---|---|
   | Cliente no existe | WARN | No debería pasar: leads crea el cliente antes que el lead |
   | Cliente anonimizado | INFO | Esperable, por ejemplo una corrección de un lead viejo |
   | Cliente activo | DEBUG | Duplicado: entrega "al menos una vez" |

Esa consulta extra solo ocurre en el camino raro. La clasificación es solo para el log; con el mismo resultado observable (0 filas) no hay ninguna rama de negocio.

**Anonimización:** `HistorialAnonimizacionListener` escucha `ClienteAnonimizadoEvento` con `@TransactionalEventListener(phase = BEFORE_COMMIT)` y llama a `historial.eliminarDelCliente(clienteId)`, igual que `SituacionCrediticiaAnonimizacionListener`. Si falla, la anonimización se revierte.

### D7. Listener, offset inicial y correlationId

- `LeadCalificadoListener` tiene `@KafkaListener(topics = "${clientes.kafka.topicos.lead-calificado}")`. Recibe `ConsumerRecord<String, byte[]>`, traduce y llama a `RegistroCalificacionService`. No captura excepciones: el error handler decide.
- `application.yml`:

  ```yaml
  spring.kafka.consumer:
    group-id: clientes-historial
    auto-offset-reset: earliest        # la primera vez carga lo que calificacion ya publicó
    key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    value-deserializer: org.apache.kafka.common.serialization.ByteArrayDeserializer
    properties:
      "[allow.auto.create.topics]": false   # no crear lead-calificado: es de calificacion
  ```

  `clientes` **no** declara un `NewTopic` para `lead-calificado`. Si el tópico no existe, el contenedor arranca igual (`missingTopicsFatal` es false por defecto), loguea la advertencia y empieza a leer cuando aparece.
- **MDC por mensaje:** el listener pone `correlationId` en el MDC durante el procesamiento y lo quita en un `finally`. Toma el del sobre, si vino y cumple el formato de `CorrelationIdFilter` (que pasa a exponer ese chequeo como método estático), o genera un UUID. Además pone `leadId` en los logs del mensaje.
- **Logs:** solo `leadId`, `clienteId`, partición y offset. Nunca `motivos` ni `observacion`. `CalificacionHistorica.toString()` los omite, por si alguien loguea el objeto entero.

### D8. Consulta

- `HistorialCalificacionesService.consultar(clienteId)` (`@Transactional(readOnly = true)`): `ConsultaClienteService.obtener` (404/410) y después `historial.delCliente`.
- `HistorialResponse(UUID clienteId, List<CalificacionResponse> calificaciones)`.
- `CalificacionResponse(UUID leadId, String nivel, int puntaje, String origen, Instant calculadaEn, String observacion, List<String> motivos)`. `observacion` se serializa como `null` si falta, y `motivos` siempre como array.
- OpenAPI documenta `nivel` y `origen` como texto con los valores conocidos de ejemplo (`MUY_POTABLE`, `POTABLE`, `NO_POTABLE`; `AUTOMATICA`, `MANUAL`), sin restringirlos a esos valores, y las respuestas 200, 404 y 410.

### D9. Configuración

- `ClientesProperties.Kafka.Topicos` suma `leadCalificado` y `leadCalificadoDlt`. El Javadoc de `Kafka` pasa de "tópicos que publica" a "tópicos que publica y consume".
- `ClientesProperties.Historial(@NotNull ZoneId zonaFecha, @Valid @NotNull Reintentos reintentos)`, con `Reintentos(@PositiveOrZero int intentos, @NotNull Duration esperaInicial, @DecimalMin("1.0") double multiplicador)`.

  ```yaml
  clientes:
    kafka:
      topicos:
        lead-calificado: lead-calificado
        lead-calificado-dlt: lead-calificado.clientes.DLT
    historial:
      zona-fecha: America/Argentina/Buenos_Aires
      reintentos:
        intentos: 5
        espera-inicial: 2s
        multiplicador: 2
  ```

- `docker-compose.yml` no cambia: `calificacion` no está en este repo, y para la prueba manual el tópico se crea con `kafka-topics.sh`.

### D10. Tests

- **Unitarios sin Spring:**
  - `LeadCalificadoTraductor`: evento plano, sobre común, `eventType` ajeno, cada campo obligatorio faltante o con tipo incorrecto, `nivel` desconocido, puntos negativos, `motivos` ausente, `null` y con un elemento no textual, fecha sin zona, con `Z` y con offset, nanosegundos truncados, campo extra ignorado, y que el mensaje de la excepción no contiene el valor;
  - `RegistroCalificacionService` con dobles del puerto y del repositorio: los cuatro caminos y su nivel de log;
  - `CalificacionHistorica.toString()` sin motivos ni observación.
- **Repositorio (Testcontainers):**
  - registro, duplicado, cliente anonimizado, cliente inexistente, orden de `delCliente` y `eliminarDelCliente`;
  - la base rechaza `motivos` que no sean array;
  - dos registros concurrentes del mismo evento dejan una sola fila.
- **Integración con Kafka (`@PruebaIntegracion`):**
  - una `@TestConfiguration` crea `lead-calificado` (el servicio no lo crea);
  - los tests publican bytes con un `KafkaTemplate<String, byte[]>` propio y esperan con Awaitility;
  - para el DLT, un `KafkaConsumer` de test lee `lead-calificado.clientes.DLT` desde el principio y filtra por un `leadId` o un texto único por test, porque el contexto y los tópicos son compartidos.
- **Fallas transitorias y persistentes:** un trigger temporal `BEFORE INSERT` en `historial_calificacion` falla mientras `nextval('prueba_fallas')` no supere un límite. Una secuencia no se revierte con el rollback, así que cuenta los intentos. Con límite 1, el segundo intento funciona; con límite alto, termina en el DLT. Cada test crea y borra su trigger y su secuencia.
- **Anonimización simultánea:** dos hilos, como en el 05. Uno abre una transacción, toma la fila del cliente con `FOR UPDATE` y anonimiza; el otro registra. Al final no hay filas.
- **Logs:** `OutputCaptureExtension` con nivel DEBUG verifica que no aparecen los textos de `motivos` ni de `observacion`.

## Risks / Trade-offs

- **La zona por defecto es incorrecta.** Si `calificacion` corre en UTC y manda `LocalDateTime` en UTC, las fechas quedan corridas 3 horas. Es configurable (`CLIENTES_HISTORIAL_ZONA_FECHA` por la relajación de nombres de Spring), se pidió confirmar la zona en el doc del equipo, y si `calificacion` empieza a mandar offset, el consumidor lo respeta sin cambios.
- **Dos calificaciones distintas del mismo lead y origen con la misma fecha.** La segunda se descarta como duplicado. Requiere dos cálculos en el mismo microsegundo, o en el mismo segundo si `calificacion` trunca. Se acepta; con el sobre común no cambia (D4).
- **Mientras dura un reintento, la partición espera** (hasta ~1 min). Las demás particiones siguen. Con la base caída, todas fallan igual, y esperar es lo correcto.
- **Mensajes en el DLT sin reproceso automático.** Quedan visibles y se republican a mano. Con la idempotencia, republicar de más es seguro.
- **Releer desde el principio en el primer despliegue o tras borrar el grupo.** Reprocesa todo el tópico, sin duplicar nada. Con la retención por defecto de Kafka (7 días), las calificaciones más viejas que eso no se recuperan. Se acepta: el historial completo sigue en `calificacion`.
- **Cambio en el formato del evento sin aviso**, como un campo renombrado. Los mensajes van al DLT y el WARN de cada uno lo hace visible. Nada se pierde y se pueden republicar después del arreglo.
- **El productor del DLT fuera de Spring.** Si se olvida cerrarlo, quedan hilos del productor al apagar. Se cierra en `destroy()` y un test de contexto lo cubre al cerrar.

## Migration Plan

1. `V5__historial_calificacion.sql` crea una tabla nueva sin tocar datos. Rollback: revertir el PR, hacer `DROP TABLE historial_calificacion` y opcionalmente borrar el grupo `clientes-historial`. La tabla se reconstruye releyendo el tópico.
2. Al desplegar, el grupo nuevo lee `lead-calificado` desde el principio y carga lo que esté retenido.
3. Publicar `docs/contrato-equipo/clientes-08-para-contratos-backend.md` antes de avisar al front. Las sugerencias a `calificacion` no bloquean el despliegue.

## Open Questions

- ¿En qué zona genera `calificacion` la `fecha`? Si es UTC, se configura `zona-fecha: UTC`, o mejor, `calificacion` pasa a mandar `Instant`.
- ¿`calificacion` va a sumar el usuario de las correcciones? Cuando lo haga, se agrega como campo opcional de la respuesta, sin migrar datos viejos (quedan `null`).
