# Microservicio `clientes` — Documento semilla para SDD con OpenSpec

| Campo | Detalle |
| --- | --- |
| Proyecto | CRM de calificación de leads · Desarrollo de Aplicaciones II · UADE |
| Servicio | `clientes-service` (Servicio de Clientes de la consigna) |
| Fuente | Documento de arquitectura v1.0 (septiembre 2026) |
| Estado | Borrador para revisión del equipo |

Este documento **no reemplaza** al documento de arquitectura: baja a nivel de implementación todo lo que corresponde al servicio `clientes`, propone funcionalidades CRM orientadas a vender más y lista ajustes de arquitectura que hay que acordar con el equipo (sección 3). Está organizado para que Claude Code lo use como fuente al crear los *changes* de OpenSpec.

---

## 0. Cómo usar este documento con OpenSpec

### 0.1 Preparación (una vez, en la raíz del monorepo)

```bash
npm install -g @fission-ai/openspec@latest
openspec --version
openspec init --tools claude
mkdir -p docs/specs-seed && cp clientes-service-spec.md docs/specs-seed/
```

El monorepo es compartido: hay **una sola carpeta `openspec/`** para todo el equipo. Para no pisarse, todos los changes y capabilities de este servicio llevan el prefijo `clientes-`.

Copiá el bloque de la sección 1 como contexto del proyecto. Según la versión de OpenSpec, va en `openspec/config.yaml` (campo `context:`) o en `openspec/project.md`. Si otro integrante ya lo creó, agregá solo lo específico de `clientes`.

### 0.2 Ciclo por cada change (en el orden de la sección 6)

1. Crear el change: `/opsx:propose clientes-0N-...` (en versiones anteriores: `/opsx:new` + `/opsx:ff`, o `/openspec:proposal`). Usar el prompt de 0.3.
2. Validar: `openspec validate clientes-0N-... --strict`.
3. Revisar `proposal.md`, specs, `design.md` y `tasks.md` antes de implementar. Si algo no cierra, se corrige el spec, no el código.
4. Implementar: `/opsx:apply clientes-0N-...`.
5. Tests en verde, PR con revisión, y al mergear: `/opsx:archive clientes-0N-...` (fusiona los deltas en `openspec/specs/`).

### 0.3 Prompt sugerido para Claude Code

```text
Leé docs/specs-seed/clientes-service.md (es la fuente de verdad para el servicio clientes).
Creá el change `clientes-02-registro-y-consulta` usando la sección 6.2:
- proposal.md: copiá el bloque "proposal.md" de esa sección.
- specs/: copiá textualmente el bloque "spec delta" en la ruta indicada.
- design.md: generalo a partir de la sección 5, solo con lo que aplica a este change.
- tasks.md: copiá el bloque "tasks.md".
No agregues requisitos que no estén en el documento; si detectás un hueco, listalo como
pregunta abierta en design.md en lugar de resolverlo por tu cuenta.
Al terminar corré `openspec validate clientes-02-registro-y-consulta --strict`.
```

### 0.4 Convenciones de los specs

Los requisitos se escriben en español, pero conservan las palabras clave que OpenSpec valida: cada requisito contiene **MUST** o **MUST NOT**, cada requisito tiene al menos un `#### Scenario:` y los escenarios usan **WHEN / THEN / AND**.

---

## 1. Contexto del proyecto (pegar en la configuración de OpenSpec)

```markdown
## Proyecto
CRM de calificación de leads para una agencia de vehículos usados (autos, motos, camionetas).
Trabajo práctico de Desarrollo de Aplicaciones II (UADE). Arquitectura de microservicios.

## Stack
- Java 21, Spring Boot 3, Maven multi-módulo (monorepo), módulo `contracts` compartido.
- PostgreSQL 16 (una base por servicio en una sola instancia), Spring Data JPA, Flyway.
- Apache Kafka (KRaft) con Spring for Apache Kafka. Spring Cloud Gateway como entrada única.
- springdoc-openapi, Resilience4j, libphonenumber.
- Tests: JUnit 5, Mockito, AssertJ, Testcontainers (PostgreSQL y Kafka).
- Docker y Docker Compose para el entorno de laboratorio.

## Reglas de arquitectura (no negociables)
- Cada servicio es dueño de su base. Ningún servicio lee la base de otro. Relaciones entre servicios por ID.
- Capas: api → application → domain ← infrastructure. El dominio no depende de Spring Data, Kafka ni clientes HTTP; usa puertos.
- REST cuando el que llama necesita la respuesta; eventos Kafka cuando solo avisa que algo pasó.
- Eventos de integración: sobre común (eventId, eventType, version, occurredAt, correlationId, payload), records en `contracts`, clave = ID del agregado. Sin datos personales en los eventos.
- Publicación de eventos después del commit (listener AFTER_COMMIT). Outbox queda para el Integrador.
- Errores REST en ProblemDetail (RFC 7807) con campo `codigo`.
- Configuración externalizada (umbrales, URLs, claves) por variables de entorno y perfiles.

## Servicio clientes
- Paquete base: com.uade.crm.clientes. Puerto interno 8082. Base: clientes_db.
- Único servicio que conoce el CUIL (Ley 25.326, principio de minimización). El CUIL se guarda cifrado,
  nunca viaja en eventos, URLs ni logs, y en las respuestas siempre va enmascarado.

## Convenciones
- Ramas feature/clientes-{tema}. Commits: tipo(clientes): descripción.
- Changes de OpenSpec con prefijo clientes-NN-. Capabilities con prefijo clientes-.
- Cada change agrega su propia migración Flyway (V1, V2, ...). Nunca se edita una migración ya mergeada.
- Código, nombres de dominio y mensajes en español; palabras clave de los specs (MUST, WHEN, THEN) en inglés.
```

---

## 2. Visión del servicio: de "guardar datos" a "conocer al cliente"

El documento de arquitectura define a `clientes` como el guardián de los datos personales y del CUIL. Eso es correcto, pero se queda corto para un CRM: un servicio de clientes que solo hace altas y consultas no ayuda a vender. La propuesta es que `clientes` sea **la ficha única del comprador**, con la información que necesitan el telemarketer y el vendedor para no perder negocios, sin invadir lo que le corresponde a `leads` (el ciclo de vida de cada negocio) ni a `calificacion` (el puntaje).

La regla para decidir qué va en este servicio: **si el dato describe a la persona y sobrevive a un negocio puntual, va en `clientes`; si describe una operación concreta (unidad elegida, anticipo, permuta), va en `leads`.**

| Dolor relevado en la agencia | Funcionalidad que lo ataca en `clientes` |
| --- | --- |
| La misma persona escribe por Instagram, después por TikTok y después por WhatsApp; hoy son tres fichas y nadie sabe que ya se habló con ella. | Alta con deduplicación por teléfono y CUIL, con contador de contactos y último canal. Un cliente que consulta varias veces es una señal de interés. |
| El vendedor tiene que rellamar porque le falta información del prospecto. | Notas del cliente acumuladas a lo largo de todos sus contactos, y ficha con situación crediticia lista. |
| El Veraz o la situación crediticia no llega en el dato. | Situación crediticia con caché, vigencia y degradación controlada si la fuente no responde. |
| Un prospecto "no potable" para una unidad de 10 millones puede ser "muy potable" para una de 5 millones, pero hoy se pierde. | Preferencias de vehículo y búsqueda de interesados: cuando entra una unidad, se sabe a quién ofrecérsela. |
| No se sabe qué canal trae mejores clientes. | Canal de origen (primer contacto) y último canal, disponibles para cruzar con la calificación. |
| Se manejan DNI, CUIL y teléfonos en cuadernos y chats. | Consentimiento por finalidad y revocable, CUIL cifrado, baja por anonimización (derecho de supresión). |

### 2.1 Requerimientos funcionales del servicio

Se proponen IDs propios (`RFC-`) que se pueden incorporar a la tabla 5.2 del documento de arquitectura. Entre paréntesis, el RF original que cubren.

| ID | Requerimiento | Entrega | Change |
| --- | --- | --- | --- |
| RFC-01 | Alta de cliente idempotente con deduplicación por teléfono. | Primera (núcleo) | 02 |
| RFC-02 | Consulta de la ficha por ID y búsqueda paginada por teléfono, nombre y provincia. | Primera (núcleo) | 02 |
| RFC-03 | Edición de datos con control de concurrencia optimista. | Primera (núcleo) | 03 |
| RFC-04 | Baja por anonimización (derecho de supresión, Ley 25.326). | Primera (núcleo) | 03 |
| RFC-05 | Eventos ClienteActualizado y ClienteAnonimizado para mantener actualizadas las copias de otros servicios. | Primera (núcleo) | 03 |
| RFC-06 | Registro de CUIL validado y cifrado, con consentimiento informado (RF-02). | Primera (núcleo) | 04 |
| RFC-07 | Consentimientos por finalidad: otorgar, consultar y revocar. | Primera (núcleo) | 04 |
| RFC-08 | Situación crediticia con caché, vigencia, adaptador simulado y degradación (RF-06). | Primera (núcleo) | 05 |
| RFC-09 | Notas del cliente, inmutables y ordenadas por fecha. | Primera (deseable) | 06 |
| RFC-10 | Preferencias de vehículo y búsqueda de clientes interesados en una unidad. | Primera (deseable) | 07 |
| RFC-11 | Reactivación automática: al ingresar una unidad o bajar un precio, detectar interesados y publicar una oportunidad. | Segunda | backlog |
| RFC-12 | Adaptador SOAP de precalificación contra el BCRA (RF-14). | Segunda | backlog |
| RFC-13 | Outbox para la publicación de eventos y auditoría de accesos al CUIL. | Integrador | backlog |

---

## 3. Ajustes propuestos a la arquitectura

Cada ajuste indica a quién afecta. Los que tocan otros servicios **hay que acordarlos con sus responsables antes de implementarlos**; el diseño de `clientes` funciona con cualquiera de las dos opciones, salvo que se indique lo contrario.

| ID | Ajuste | Por qué | Impacto |
| --- | --- | --- | --- |
| AC-01 | **El frontend registra al cliente antes que el lead.** El preformulario hace `POST /api/clientes` (con CUIL y consentimientos si los hay), recibe el `clienteId` y después hace `POST /api/leads` con ese ID y los datos del negocio. | Hoy el CUIL viaja por `leads` para llegar a `clientes`, lo que contradice AD-04 en la práctica. Con este ajuste el CUIL entra directo al único servicio que lo puede ver, y `leads` se simplifica. Como el alta es idempotente, un reintento del frontend no duplica nada; si falla el segundo paso queda un cliente sin lead, que se reutiliza en el próximo intento. | Frontend y `leads`. La comunicación sincrónica de la consigna se mantiene en `calificacion → clientes` y `calificacion → vehiculos`; si el equipo quiere conservar también `leads → clientes`, `leads` puede validar el `clienteId` con `GET /clientes/{id}`. |
| AC-02 | **Alta con deduplicación (upsert).** `POST /clientes` busca por CUIL y por teléfono normalizado; si encuentra, devuelve el cliente existente con 200. | Evita fichas duplicadas por multicanal, hace seguro el reintento y genera la señal "cliente recurrente". | Solo `clientes`. |
| AC-03 | **`clientes` publica `ClienteActualizado` y `ClienteAnonimizado`.** | `leads` guarda nombre y teléfono como copia para la cola. Si se edita o se borra un cliente, esa copia queda desactualizada; el documento dice que `clientes` no publica eventos, y ahí hay un hueco. Los eventos solo llevan el `clienteId` y los nombres de los campos modificados (sin datos personales); `leads` vuelve a pedir la ficha por REST. | `leads` agrega un consumidor. Alternativa descartada: que `leads` no guarde copia y consulte siempre a `clientes`, lo que multiplica las llamadas al armar la cola. |
| AC-04 | **La baja es una anonimización, no un borrado físico.** | Otros servicios referencian el `clienteId` (leads, historial de calificaciones). Borrar la fila rompe esas referencias; anonimizar cumple el derecho de supresión y conserva la integridad. | Solo `clientes`, más el evento de AC-03. |
| AC-05 | **La situación crediticia devuelve un estado explícito** (`CON_DATOS`, `SIN_REGISTROS`, `SIN_CUIL`, `SIN_CONSENTIMIENTO`, `NO_DISPONIBLE`) además del número 1 a 6, y nunca responde 5xx por culpa de la fuente externa. | `calificacion` necesita distinguir "no hay datos" de "hay datos malos" sin lógica de errores. Coincide con la mitigación de riesgos del documento (falta de respuesta = sin datos = 0 puntos). | `calificacion` mapea: `CON_DATOS` con 1–2 → +20; `CON_DATOS` con 3–6 → −25; cualquier otro estado → 0. El enum va en `contracts`. |
| AC-06 | **`vehiculos` agrega el atributo `tipo`** (AUTO, MOTO, CAMIONETA, SUV, UTILITARIO) y los enums compartidos (`Provincia`, `CanalOrigen`, `TipoVehiculo`) van en `contracts`. | El negocio vende distintos tipos de vehículo y las preferencias y la búsqueda de interesados los necesitan. `Provincia` como enum compartido evita que `leads`, `clientes` y la regla de ubicación escriban "Bs As" de tres formas. | `vehiculos` y `contracts`. Sin este ajuste, el change 07 usa el enum local y se migra después. |
| AC-07 | **El CUIL nunca va en una URL.** La búsqueda por CUIL es `POST /clientes/busqueda-por-cuil`. | Las URLs quedan en logs del gateway, de nginx y del navegador. | Gateway: no loguear cuerpos de requests. |

### 3.1 Simplificaciones

| ID | Simplificación | Ahorro |
| --- | --- | --- |
| S-01 | La caché de situación crediticia vive en la base del servicio, no en Redis ni en memoria. | Una pieza de infraestructura menos, y varias réplicas comparten la caché. |
| S-02 | Las notas son inmutables: se agregan, no se editan ni se borran (salvo por anonimización). | Sin control de concurrencia ni auditoría de cambios en notas. |
| S-03 | Sin autenticación en la Primera Parte: el autor de una nota viene en el header `X-Usuario`. | Se reemplaza por el usuario autenticado cuando se defina la autenticación en el Integrador. |
| S-04 | Servicios de aplicación por caso de uso (`AltaClienteService`, `EdicionClienteService`, ...) en lugar de una fachada única. | El patrón Facade ya está cubierto en `calificacion`; en `clientes` agregaría una capa sin valor. |
| S-05 | Una migración Flyway por change. | Cada PR es autocontenido y se puede revisar y revertir por separado. |

---

## 4. Alcance del servicio por entrega

| Entrega | Qué construye `clientes` |
| --- | --- |
| Primera Parte | Changes 01 a 05 (núcleo) y, si hay tiempo, 06 y 07. Adaptador simulado de situación crediticia. Publica `cliente-actualizado` y `cliente-anonimizado`. |
| Segunda Parte | Adaptador SOAP contra el servicio `precalificacion` (cambia solo la infraestructura gracias al puerto). Reactivación por eventos de stock (RFC-11), que demuestra integración asincrónica consumiendo eventos de `vehiculos`. |
| Integrador | Outbox, auditoría de accesos al CUIL con usuario autenticado, pruebas de carga y de integración completas. |

---

## 5. Diseño (fuente para los `design.md`)

### 5.1 Estructura de paquetes

```text
com.uade.crm.clientes
├── api
│   ├── ClienteController, ConsentimientoController, SituacionCrediticiaController,
│   │   NotaController, PreferenciaController
│   ├── dto            requests/responses (records), mapeadores
│   └── error          ApiExceptionHandler (@RestControllerAdvice → ProblemDetail)
├── application
│   ├── AltaClienteService, EdicionClienteService, BajaClienteService
│   ├── CuilService, ConsentimientoService, SituacionCrediticiaService
│   ├── NotaService, PreferenciaService, InteresadosService
│   └── listener       ClienteEventosListener (@TransactionalEventListener AFTER_COMMIT → Kafka)
├── domain
│   ├── model          Cliente, Consentimiento, SituacionCrediticia, Nota, PreferenciaVehiculo,
│   │                  EstadoCliente, Finalidad, EstadoSituacion, Coincidencia
│   ├── valor          Telefono, Cuil (value objects con validación y enmascarado)
│   ├── event          ClienteActualizadoEvento, ClienteAnonimizadoEvento (eventos de dominio)
│   ├── port           SituacionCrediticiaPort, CifradorCuil
│   └── ClienteFactory construye y normaliza clientes nuevos
├── infrastructure
│   ├── persistence    entidades JPA, repositorios Spring Data, converters
│   ├── crypto         AesGcmCifradorCuil (AES-256-GCM + HMAC-SHA256)
│   ├── precalificacion SimuladaSituacionAdapter (Primera), BcraSoapSituacionAdapter (Segunda)
│   └── messaging      ClienteEventosPublisher (KafkaTemplate)
└── config             ClientesProperties, Resilience4j, Kafka, OpenAPI, CorrelationIdFilter
```

### 5.2 Modelo de datos (por migración)

> **Nota (change 03, 2026-10-08):** las migraciones se numeran en el orden en que se mergean (S-05). El change 03
> agregó `V2__no_contactar.sql` (marca no contactar y check de anonimización), así que los números de abajo se
> corren uno: CUIL y consentimientos (04) pasa a `V3`, situación crediticia (05) a `V4`, notas (06) a `V5` y
> preferencias (07) a `V6`. Además, `V1` real usa índice único sobre `email` (no sobre teléfono) y separa
> `nombre`/`apellido`: ver `openspec/specs/` y los changes archivados.

```sql
-- V1__cliente.sql  (change 02)
CREATE TABLE cliente (
    id                     UUID PRIMARY KEY,
    nombre                 VARCHAR(120) NOT NULL,
    telefono_e164          VARCHAR(20),
    telefono_alternativo   VARCHAR(20),
    email                  VARCHAR(254),
    provincia              VARCHAR(40)  NOT NULL,
    localidad              VARCHAR(120),
    canal_origen           VARCHAR(20)  NOT NULL,
    ultimo_canal           VARCHAR(20)  NOT NULL,
    contactos_registrados  INT          NOT NULL DEFAULT 1,
    ultimo_contacto_en     TIMESTAMPTZ  NOT NULL,
    requiere_revision      BOOLEAN      NOT NULL DEFAULT FALSE,
    motivo_revision        VARCHAR(40),
    estado                 VARCHAR(15)  NOT NULL DEFAULT 'ACTIVO',  -- ACTIVO | ANONIMIZADO
    version                BIGINT       NOT NULL DEFAULT 0,
    creado_en              TIMESTAMPTZ  NOT NULL,
    actualizado_en         TIMESTAMPTZ  NOT NULL,
    anonimizado_en         TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_cliente_telefono_activo ON cliente (telefono_e164) WHERE estado = 'ACTIVO';
CREATE INDEX ix_cliente_provincia ON cliente (provincia) WHERE estado = 'ACTIVO';

-- V2__cuil_y_consentimiento.sql  (change 04)
ALTER TABLE cliente
    ADD COLUMN cuil_cifrado  BYTEA,
    ADD COLUMN cuil_hash     CHAR(64),
    ADD COLUMN cuil_ultimos4 CHAR(4);
CREATE UNIQUE INDEX ux_cliente_cuil_activo ON cliente (cuil_hash) WHERE estado = 'ACTIVO';

CREATE TABLE consentimiento (
    id             UUID PRIMARY KEY,
    cliente_id     UUID        NOT NULL REFERENCES cliente(id),
    finalidad      VARCHAR(40) NOT NULL,  -- PRECALIFICACION_CREDITICIA | CONTACTO_COMERCIAL
    version_texto  VARCHAR(20) NOT NULL,  -- versión del texto legal aceptado, ej. "v1"
    origen         VARCHAR(20) NOT NULL,  -- PREFORMULARIO | TELEMARKETER
    otorgado_en    TIMESTAMPTZ NOT NULL,
    revocado_en    TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_consentimiento_vigente
    ON consentimiento (cliente_id, finalidad) WHERE revocado_en IS NULL;

-- V3__situacion_crediticia.sql  (change 05)
CREATE TABLE situacion_crediticia (
    cliente_id     UUID PRIMARY KEY REFERENCES cliente(id),
    estado         VARCHAR(20) NOT NULL,          -- CON_DATOS | SIN_REGISTROS (los únicos que se cachean)
    situacion      SMALLINT CHECK (situacion BETWEEN 1 AND 6),
    periodo        CHAR(6),                       -- AAAAMM informado por la fuente
    fuente         VARCHAR(20) NOT NULL,          -- SIMULADA | BCRA
    consultada_en  TIMESTAMPTZ NOT NULL,
    vigente_hasta  TIMESTAMPTZ NOT NULL
);

-- V4__nota.sql  (change 06)
CREATE TABLE nota (
    id          UUID PRIMARY KEY,
    cliente_id  UUID          NOT NULL REFERENCES cliente(id),
    texto       VARCHAR(1000) NOT NULL,
    autor       VARCHAR(60)   NOT NULL,
    creada_en   TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_nota_cliente ON nota (cliente_id, creada_en DESC);

-- V5__preferencia_vehiculo.sql  (change 07)
CREATE TABLE preferencia_vehiculo (
    id              UUID PRIMARY KEY,
    cliente_id      UUID          NOT NULL REFERENCES cliente(id),
    tipo_vehiculo   VARCHAR(20)   NOT NULL,
    marca           VARCHAR(60),
    precio_maximo   NUMERIC(15,2),
    anio_minimo     SMALLINT
);
CREATE INDEX ix_preferencia_tipo ON preferencia_vehiculo (tipo_vehiculo);
```

Las claves foráneas son internas a la base de `clientes`; hacia otros servicios solo se usan IDs.

### 5.3 API REST

Rutas internas del servicio. A través del gateway se exponen como `/api/clientes/**`.

| Método | Ruta | Descripción | Respuestas |
| --- | --- | --- | --- |
| POST | `/clientes` | Alta con deduplicación (upsert). Acepta CUIL y consentimientos opcionales. | 201 nuevo · 200 existente · 422 |
| GET | `/clientes/{id}` | Ficha con CUIL enmascarado. Devuelve `ETag`. | 200 · 404 · 410 |
| GET | `/clientes?telefono=&q=&provincia=&page=&size=` | Búsqueda paginada (máx. 50 por página). | 200 |
| PATCH | `/clientes/{id}` | Edición parcial. Requiere `If-Match`. | 200 · 409 · 412 · 428 |
| DELETE | `/clientes/{id}` | Anonimización. Idempotente. | 204 · 404 |
| PUT | `/clientes/{id}/cuil` | Registra o reemplaza el CUIL, con consentimiento. | 200 · 409 · 422 |
| POST | `/clientes/busqueda-por-cuil` | Busca un cliente por CUIL (en el cuerpo, nunca en la URL). | 200 · 404 |
| GET | `/clientes/{id}/consentimientos` | Consentimientos vigentes y revocados. | 200 |
| POST | `/clientes/{id}/consentimientos` | Otorga un consentimiento para una finalidad. | 201 · 200 si ya estaba vigente |
| DELETE | `/clientes/{id}/consentimientos/{finalidad}` | Revoca el consentimiento vigente. | 204 |
| GET | `/clientes/{id}/situacion-crediticia` | Estado y situación 1 a 6. Usado por `calificacion`. | 200 · 404 |
| POST | `/clientes/{id}/notas` | Agrega una nota. Autor en header `X-Usuario`. | 201 |
| GET | `/clientes/{id}/notas?page=&size=` | Notas, de la más nueva a la más vieja. | 200 |
| PUT | `/clientes/{id}/preferencias` | Reemplaza las preferencias (máx. 5). | 200 · 422 |
| GET | `/clientes/{id}/preferencias` | Preferencias vigentes. | 200 |
| GET | `/clientes/interesados?tipoVehiculo=&marca=&precio=&anio=&page=&size=` | Clientes con preferencias compatibles y consentimiento comercial. | 200 |

#### Ejemplos

```http
POST /clientes
Content-Type: application/json

{
  "nombre": "Martina López",
  "telefono": "11 1234-5678",
  "email": "martina@example.com",
  "provincia": "BUENOS_AIRES",
  "localidad": "Lanús",
  "canalOrigen": "INSTAGRAM",
  "cuil": "27-30111222-5",
  "consentimientos": [
    { "finalidad": "PRECALIFICACION_CREDITICIA", "versionTexto": "v1" },
    { "finalidad": "CONTACTO_COMERCIAL", "versionTexto": "v1" }
  ]
}
```

```http
HTTP/1.1 201 Created
Location: /clientes/a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d

{ "clienteId": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d", "nuevo": true, "coincidencia": "NINGUNA" }
```

```http
GET /clientes/a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d

HTTP/1.1 200 OK
ETag: W/"3"

{
  "id": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
  "nombre": "Martina López",
  "telefono": "+5491112345678",
  "telefonoAlternativo": null,
  "email": "martina@example.com",
  "provincia": "BUENOS_AIRES",
  "localidad": "Lanús",
  "cuil": "XX-XXXX1222-X",
  "canalOrigen": "INSTAGRAM",
  "ultimoCanal": "WHATSAPP",
  "contactosRegistrados": 3,
  "ultimoContactoEn": "2026-10-05T13:02:44Z",
  "requiereRevision": false,
  "consentimientosVigentes": ["PRECALIFICACION_CREDITICIA", "CONTACTO_COMERCIAL"]
}
```

```http
GET /clientes/a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d/situacion-crediticia

{
  "clienteId": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
  "estado": "CON_DATOS",
  "situacion": 1,
  "periodo": "202608",
  "fuente": "SIMULADA",
  "consultadaEn": "2026-10-05T13:02:45Z"
}
```

```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json

{
  "type": "https://crm.uade.local/errores/telefono-en-uso",
  "title": "El teléfono ya pertenece a otro cliente",
  "status": 409,
  "codigo": "TELEFONO_EN_USO",
  "instance": "/clientes/a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
  "correlationId": "c0a8012e-7f3b-4d8a-b1c2-9d8e7f6a5b4c"
}
```

### 5.4 Catálogo de errores

| `codigo` | HTTP | Cuándo |
| --- | --- | --- |
| `VALIDACION` | 422 | Falla de Bean Validation; incluye lista `errores` con `campo` y `mensaje`. |
| `JSON_INVALIDO` | 400 | Cuerpo mal formado. |
| `TELEFONO_INVALIDO` | 422 | No se puede normalizar a un móvil argentino válido. |
| `CUIL_INVALIDO` | 422 | Formato, prefijo o dígito verificador incorrecto. |
| `CONSENTIMIENTO_REQUERIDO` | 422 | Se envía CUIL sin consentimiento de precalificación. |
| `CLIENTE_NO_ENCONTRADO` | 404 | ID inexistente. |
| `CLIENTE_ANONIMIZADO` | 410 | El cliente existió y fue anonimizado. |
| `TELEFONO_EN_USO` | 409 | Edición con un teléfono de otro cliente activo. |
| `CUIL_EN_USO` | 409 | `PUT /cuil` con un CUIL de otro cliente activo. |
| `PRECONDICION_REQUERIDA` | 428 | PATCH sin `If-Match`. |
| `VERSION_DESACTUALIZADA` | 412 | `If-Match` no coincide con la versión actual. |

### 5.5 Eventos de integración que publica `clientes`

| Tópico | Evento | Clave | Consumidores | Entrega |
| --- | --- | --- | --- | --- |
| `cliente-actualizado` | `ClienteActualizado` | clienteId | leads | Primera |
| `cliente-anonimizado` | `ClienteAnonimizado` | clienteId | leads | Primera |
| `oportunidad-detectada` | `OportunidadDetectada` | vehiculoId | leads | Segunda (RFC-11) |

```json
{
  "eventId": "5d1e7c3a-2b4f-4a6e-9c8d-7e6f5a4b3c2d",
  "eventType": "ClienteActualizado",
  "version": 1,
  "occurredAt": "2026-10-05T13:10:02Z",
  "correlationId": "c0a8012e-7f3b-4d8a-b1c2-9d8e7f6a5b4c",
  "payload": {
    "clienteId": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
    "campos": ["NOMBRE", "TELEFONO"]
  }
}
```

`ClienteAnonimizado` lleva solo `clienteId`. Los eventos **no** llevan nombre, teléfono, email ni CUIL: el consumidor que necesite el dato nuevo lo pide por REST (patrón *event notification*). Los records van en `contracts` (`com.uade.crm.contracts.clientes`).

### 5.6 Reglas de normalización y validación

**Teléfono.** Se normaliza con libphonenumber (región por defecto AR) a E.164 **móvil**, porque todo el contacto con la agencia es por WhatsApp. libphonenumber interpreta `11 1234-5678` como línea fija (`+541112345678`), así que si el número argentino resultante no es móvil se inserta el `9` después de `+54` y se vuelve a validar.

| Entrada | Normalizado |
| --- | --- |
| `11 1234-5678` | `+5491112345678` |
| `011 15 1234-5678` | `+5491112345678` |
| `+54 9 11 1234-5678` | `+5491112345678` |
| `0351 15 412-3456` | `+5493514123456` |
| `1512345678` (sin característica) | 422 `TELEFONO_INVALIDO` |

**CUIL.** Acepta con o sin guiones; se guarda sin guiones. Once dígitos, prefijo de persona humana (20, 23, 24 o 27) y dígito verificador por módulo 11: se multiplican los diez primeros dígitos por `5,4,3,2,7,6,5,4,3,2`, se suma, `v = 11 − (suma mod 11)`; si `v = 11` el verificador es 0, si `v = 10` el CUIL es inválido.

| CUIL | Resultado |
| --- | --- |
| `20-12345678-6` | Válido |
| `20123456786` | Válido (sin guiones) |
| `27-30111222-5` | Válido |
| `23-30000002-9` | Válido (prefijo 23) |
| `20-12345678-5` | 422 `CUIL_INVALIDO` (verificador) |
| `30-71234567-1` | 422 `CUIL_INVALIDO` (prefijo de persona jurídica) |

**Enmascarado.** CUIL: `XX-XXXX` + últimos 4 dígitos del DNI + `-X` (ej. `XX-XXXX5678-X`). Teléfono en logs: `+54911****5678`. En las respuestas de la API el teléfono va completo porque el telemarketer lo necesita para contactar.

**Otros campos.** `nombre` 2 a 120 caracteres, recortado; `email` opcional con formato válido; `provincia` del enum `Provincia` (24 jurisdicciones); `canalOrigen` del enum `CanalOrigen` (INSTAGRAM, TIKTOK, MARKETPLACE, FACEBOOK, META_ADS, WHATSAPP, REFERIDO, OTRO).

### 5.7 Algoritmo de alta con deduplicación

> **Nota (change 04, 2026-10-09):** la deduplicación base es por **email**, no por teléfono (change 02), así que el
> orden implementado es **CUIL → email**. Coincidencia por CUIL: recontacto de ese cliente aunque el email sea otro
> (el email informado se descarta; si pertenece a otro cliente activo, se marca `POSIBLE_DUPLICADO`). Coincidencia
> por email con otro CUIL: no se toca el CUIL ni se registra el consentimiento, y se marca `CUIL_DISTINTO`. Se
> conserva el primer motivo de revisión. El paso 5 ya no responde a un endpoint público: solo `leads` llama al alta.
> Ver `openspec/specs/clientes-privacidad` y el design del change 04.

1. Normalizar el teléfono y, si viene, validar el CUIL. Si viene CUIL sin consentimiento `PRECALIFICACION_CREDITICIA`, responder 422.
2. Si viene CUIL y existe un cliente `ACTIVO` con ese `cuil_hash`: coincidencia por **CUIL**. Si el teléfono informado es distinto del registrado, guardarlo en `telefono_alternativo` (no se pisa el principal).
3. Si no, si existe un cliente `ACTIVO` con ese teléfono: coincidencia por **TELEFONO**. Si viene CUIL y el cliente no tiene, se registra. Si el cliente ya tiene otro CUIL, no se modifica y se marca `requiere_revision = true` con motivo `CUIL_DISTINTO` (puede ser un familiar que usa el mismo teléfono; lo resuelve el telemarketer).
4. Si no hay coincidencia, crear el cliente con `ClienteFactory` y responder 201.
5. Ante una coincidencia, el alta pública **no pisa** nombre ni provincia; solo completa campos vacíos (email, localidad), suma 1 a `contactos_registrados`, actualiza `ultimo_contacto_en` y `ultimo_canal`, y registra los consentimientos nuevos (si ya hay uno vigente de esa finalidad, se conserva). Responde 200.
6. Si dos altas simultáneas chocan contra el índice único, capturar `DataIntegrityViolationException`, releer y tratarlo como coincidencia (200).

La razón del paso 5: el endpoint es público (lo usa el preformulario), y sin esa regla cualquiera podría cambiar el nombre de otro cliente enviando su teléfono.

### 5.8 Protección de datos

- **Cifrado del CUIL en reposo:** AES-256-GCM con IV aleatorio de 12 bytes antepuesto al texto cifrado, en `cuil_cifrado`. Clave en `CLIENTES_CUIL_CLAVE` (base64, 32 bytes).
- **Búsqueda sin descifrar:** `cuil_hash` = HMAC-SHA256 del CUIL normalizado con `CLIENTES_CUIL_HMAC` (índice ciego). Permite unicidad y búsqueda exacta.
- **Mostrar sin descifrar:** `cuil_ultimos4` alimenta el enmascarado. Solo se descifra al consultar la situación crediticia.
- **Logs:** ningún `toString()` de entidad o DTO incluye CUIL ni teléfono completos; test automático que captura la salida de logs durante un alta con CUIL y verifica que no contiene el CUIL.
- **Consentimiento por finalidad:** sin `PRECALIFICACION_CREDITICIA` vigente no se consulta la situación crediticia; sin `CONTACTO_COMERCIAL` vigente el cliente no aparece en la búsqueda de interesados.
- **Anonimización:** nombre → `"ANONIMIZADO"`; teléfono, teléfono alternativo, email, localidad y CUIL (cifrado, hash y últimos 4) → null; se revocan los consentimientos vigentes; se borran situación crediticia, notas y preferencias; se conservan `id`, `provincia`, `canal_origen` y fechas para estadísticas.
- **Datos de prueba:** siempre ficticios (sección 7).

### 5.9 Configuración

```yaml
server:
  port: 8082

clientes:
  telefono:
    region-por-defecto: AR
  cuil:
    clave-cifrado: ${CLIENTES_CUIL_CLAVE}
    clave-hash: ${CLIENTES_CUIL_HMAC}
  situacion-crediticia:
    modo: simulado            # simulado | bcra (Segunda Parte)
    vigencia: 30d
    timeout: 2s
    simulado:
      demora: 0ms
      forzar-no-disponible: false
  interesados:
    tolerancia-precio: 0.10   # acepta unidades hasta 10% por encima del precio máximo (margen de negociación)
  paginacion:
    tamanio-maximo: 50
  kafka:
    topicos:
      cliente-actualizado: cliente-actualizado
      cliente-anonimizado: cliente-anonimizado

resilience4j:
  circuitbreaker:
    instances:
      situacionCrediticia:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
```

### 5.10 Adaptador simulado de situación crediticia

Determinístico para que los tests y la demo sean reproducibles. Usa el último dígito del DNI (posición 10 del CUIL):

| Último dígito del DNI | Resultado | CUIL de ejemplo |
| --- | --- | --- |
| 0 a 4 | `CON_DATOS`, situación 1 | `27-30111222-5`, `27-38765430-0` |
| 5 o 6 | `CON_DATOS`, situación 2 | `20-41000006-8` |
| 7 | `CON_DATOS`, situación 3 | `20-35000007-1` |
| 8 | `CON_DATOS`, situación 5 | `20-12345678-6` |
| 9 | `SIN_REGISTROS` | `20-28555449-8` |

Con `forzar-no-disponible: true` lanza una excepción de fuente caída, y con `demora` mayor al `timeout` simula una fuente lenta; ambos casos deben terminar en `NO_DISPONIBLE`. El período informado es el mes anterior al actual (`AAAAMM`).

### 5.11 Escalabilidad

- **Servicio sin estado:** se puede levantar más de una réplica detrás del gateway; la caché de situación crediticia está en la base, así que las réplicas la comparten.
- **Operaciones idempotentes:** el alta (upsert), `PUT /cuil`, `PUT /preferencias` y `DELETE` se pueden reintentar sin efectos duplicados, lo que hace seguros los reintentos del frontend y de otros servicios.
- **Índices parciales** sobre clientes activos para teléfono, CUIL y provincia; paginación obligatoria con tope.
- **Volumen de referencia:** 150 a 200 contactos diarios son unos 55.000 a 70.000 registros por año en el peor caso (sin deduplicar); la búsqueda por nombre con `ILIKE` alcanza. Si creciera, el siguiente paso es la extensión `pg_trgm` con un índice GIN sobre `nombre`.
- **Eventos con clave `clienteId`:** garantizan orden por cliente si se agregan particiones.
- **Precalificación detrás de un puerto con circuit breaker:** una fuente externa lenta no degrada el resto del servicio.

### 5.12 Patrones de diseño en `clientes`

| Patrón | Dónde | Qué resuelve |
| --- | --- | --- |
| Factory | `ClienteFactory` | Crea un cliente válido desde el alta: normaliza teléfono, recorta nombre, fija canales y fechas. |
| Repository | Repositorios Spring Data por agregado | Aísla al dominio de JPA. |
| Strategy + Adapter | `SituacionCrediticiaPort` con `SimuladaSituacionAdapter` y `BcraSoapSituacionAdapter`, elegidos por `clientes.situacion-crediticia.modo` | Cambiar la fuente en la Segunda Parte sin tocar el dominio (AD-09). |
| Observer | Eventos de dominio `ClienteActualizadoEvento` y `ClienteAnonimizadoEvento` → listener AFTER_COMMIT → Kafka | El servicio de edición no conoce Kafka ni a los consumidores. |
| Value Object | `Telefono`, `Cuil` | La validación y el enmascarado viven en un solo lugar. |

---

## 6. Changes de OpenSpec

Orden de implementación. Cada change es un PR. Los changes 01 a 05 son el núcleo de la Primera Parte; 06 y 07 son deseables.

| # | Change | Capability | Depende de |
| --- | --- | --- | --- |
| 01 | `clientes-01-base-servicio` | `clientes-transversal` | — |
| 02 | `clientes-02-registro-y-consulta` | `clientes-gestion` | 01 |
| 03 | `clientes-03-edicion-y-baja` | `clientes-gestion`, `clientes-eventos` | 02 |
| 04 | `clientes-04-cuil-y-consentimientos` | `clientes-privacidad` | 02 |
| 05 | `clientes-05-situacion-crediticia` | `clientes-situacion-crediticia` | 04 |
| 06 | `clientes-06-notas` | `clientes-seguimiento` | 02 |
| 07 | `clientes-07-preferencias-e-interesados` | `clientes-seguimiento` | 04 (consentimiento comercial) |

### 6.1 `clientes-01-base-servicio`

**proposal.md**

````markdown
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
````

**spec delta** → `openspec/changes/clientes-01-base-servicio/specs/clientes-transversal/spec.md`

````markdown
## ADDED Requirements

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
````

**tasks.md**

````markdown
# Tasks

## 1. Estructura del módulo
- [ ] 1.1 Crear `clientes-service` en el POM padre con dependencias: web, validation, data-jpa, flyway, postgresql, spring-kafka, actuator, springdoc-openapi, resilience4j, libphonenumber; test: testcontainers (postgresql, kafka), assertj
- [ ] 1.2 Crear la estructura de paquetes de la sección 5.1 del documento semilla
- [ ] 1.3 `application.yml` con perfiles `local` y `docker`; `ClientesProperties` con `@ConfigurationProperties` (sección 5.9)
- [ ] 1.4 Configurar Flyway apuntando a `clientes_db` (las tablas llegan en los changes siguientes)

## 2. Transversal
- [ ] 2.1 `ApiExceptionHandler` con ProblemDetail, campo `codigo` y `correlationId`
- [ ] 2.2 `CorrelationIdFilter` + MDC; interceptor para RestClient; header en mensajes Kafka
- [ ] 2.3 Utilidad `Enmascarador` para CUIL y teléfono; patrón de logback sin datos personales
- [ ] 2.4 springdoc con título, versión y descripción del servicio

## 3. Infraestructura
- [ ] 3.1 Dockerfile multi-stage (build con Maven, runtime JRE 21)
- [ ] 3.2 Servicio `clientes` en docker-compose: puerto interno 8082, healthcheck con actuator, variables `CLIENTES_CUIL_CLAVE` y `CLIENTES_CUIL_HMAC`
- [ ] 3.3 Ruta `/api/clientes/**` en el gateway (coordinar con el responsable)

## 4. Tests
- [ ] 4.1 Test de arranque de contexto con Testcontainers
- [ ] 4.2 Tests de los escenarios de errores (404, 422, 400)
- [ ] 4.3 Test de correlationId con y sin header
- [ ] 4.4 Test de logs con `OutputCaptureExtension` (escenario de datos personales)
````

### 6.2 `clientes-02-registro-y-consulta`

**proposal.md**

````markdown
# Proposal: Registro y consulta de clientes

## Why
La agencia recibe la misma persona por varios canales y hoy termina con fichas duplicadas, sin saber
que ya habló con ella. El alta tiene que ser idempotente y reconocer al cliente existente, y el
telemarketer tiene que poder encontrarlo por teléfono o nombre.

## What Changes
- `POST /clientes` con deduplicación por teléfono normalizado: 201 si es nuevo, 200 si ya existía.
- Normalización de teléfono a E.164 móvil argentino.
- Registro de canal de origen, último canal, cantidad de contactos y fecha del último contacto.
- `GET /clientes/{id}` con ETag de versión.
- `GET /clientes` con búsqueda paginada por teléfono, nombre y provincia.
- Migración `V1__cliente.sql`.

## Impact
- Specs: `clientes-gestion` (nueva).
- Contracts: enums `Provincia` y `CanalOrigen` (AC-06).
- Frontend / leads: consumen el alta (ver AC-01).
````

**spec delta** → `openspec/changes/clientes-02-registro-y-consulta/specs/clientes-gestion/spec.md`

````markdown
## ADDED Requirements

### Requirement: Alta de cliente con deduplicación por teléfono
El servicio MUST registrar un cliente a partir de nombre, teléfono, provincia, canal de origen y,
opcionalmente, email y localidad. Si ya existe un cliente activo con el mismo teléfono normalizado,
MUST NOT crear uno nuevo y MUST devolver el existente.

#### Scenario: Cliente nuevo
- **WHEN** se envía `POST /clientes` con un teléfono que no pertenece a ningún cliente activo
- **THEN** responde 201 con `clienteId`, `nuevo` = true y `coincidencia` = `NINGUNA`
- **AND** incluye el header `Location` con la ruta del cliente
- **AND** el cliente queda con `contactosRegistrados` = 1 y `ultimoCanal` igual a `canalOrigen`

#### Scenario: Mismo teléfono en otro formato
- **WHEN** existe un cliente activo con teléfono `+5491112345678`
- **AND** se envía `POST /clientes` con teléfono `011 15 1234-5678`
- **THEN** responde 200 con el `clienteId` existente, `nuevo` = false y `coincidencia` = `TELEFONO`

#### Scenario: Altas simultáneas con el mismo teléfono
- **WHEN** llegan dos `POST /clientes` concurrentes con el mismo teléfono
- **THEN** existe un único cliente activo con ese teléfono
- **AND** ambas respuestas devuelven el mismo `clienteId`

### Requirement: El alta pública no sobrescribe datos existentes
Cuando el alta coincide con un cliente existente, el servicio MUST NOT modificar su nombre ni su provincia;
MUST completar solo los campos que estén vacíos, sumar 1 a `contactosRegistrados`
y actualizar `ultimoContactoEn` y `ultimoCanal`.

#### Scenario: Recontacto por otro canal
- **WHEN** existe un cliente "Martina López" de BUENOS_AIRES, sin email, con canal de origen INSTAGRAM y 1 contacto
- **AND** se envía `POST /clientes` con el mismo teléfono, nombre "Martu", provincia CORDOBA, email "m@example.com" y canal WHATSAPP
- **THEN** el cliente conserva nombre "Martina López" y provincia BUENOS_AIRES
- **AND** queda con email "m@example.com", `canalOrigen` INSTAGRAM, `ultimoCanal` WHATSAPP y `contactosRegistrados` = 2

### Requirement: Normalización del teléfono
El servicio MUST normalizar el teléfono al formato E.164 de móvil argentino (`+549` seguido de característica y número)
y MUST rechazar los números que no se puedan normalizar.

#### Scenario: Número sin el 9 de móvil
- **WHEN** se envía el teléfono `11 1234-5678`
- **THEN** se guarda como `+5491112345678`

#### Scenario: Número con 0 y 15
- **WHEN** se envía el teléfono `0351 15 412-3456`
- **THEN** se guarda como `+5493514123456`

#### Scenario: Número incompleto
- **WHEN** se envía el teléfono `1512345678`
- **THEN** responde 422 con `codigo` = `TELEFONO_INVALIDO`

### Requirement: Validación de los datos de alta
El servicio MUST exigir nombre de 2 a 120 caracteres, provincia del enum `Provincia` y canal de origen del enum `CanalOrigen`,
y MUST validar el formato del email cuando se informa.

#### Scenario: Provincia inexistente
- **WHEN** se envía `POST /clientes` con provincia "BS AS"
- **THEN** responde 422 con `codigo` = `VALIDACION` y un error en el campo `provincia`

### Requirement: Consulta de la ficha del cliente
El servicio MUST devolver la ficha de un cliente activo por su ID, con el header `ETag` igual a su versión.

#### Scenario: Cliente existente
- **WHEN** se consulta `GET /clientes/{id}` de un cliente activo
- **THEN** responde 200 con nombre, teléfono, email, provincia, localidad, canales, cantidad de contactos y fecha del último contacto
- **AND** el header `ETag` contiene la versión actual del cliente

### Requirement: Búsqueda paginada de clientes
El servicio MUST permitir buscar clientes activos por teléfono (coincidencia exacta luego de normalizar),
por nombre (contiene, sin distinguir mayúsculas) y por provincia, con paginación de hasta 50 resultados por página,
ordenados por `ultimoContactoEn` descendente.

#### Scenario: Búsqueda por teléfono en formato libre
- **WHEN** se consulta `GET /clientes?telefono=11 1234 5678`
- **THEN** devuelve el cliente cuyo teléfono normalizado es `+5491112345678`

#### Scenario: Tamaño de página excedido
- **WHEN** se consulta `GET /clientes?size=500`
- **THEN** la página devuelta tiene como máximo 50 elementos
````

**tasks.md**

````markdown
# Tasks

## 1. Contracts y persistencia
- [ ] 1.1 Agregar enums `Provincia` (24 jurisdicciones) y `CanalOrigen` en `contracts` (coordinar con el responsable)
- [ ] 1.2 Migración `V1__cliente.sql` (sección 5.2)
- [ ] 1.3 Entidad JPA `ClienteEntity` con `@Version`, repositorio y consultas por teléfono activo

## 2. Dominio
- [ ] 2.1 Value object `Telefono` con normalización (libphonenumber + inserción del 9) y enmascarado
- [ ] 2.2 Modelo `Cliente` con `registrarRecontacto(...)` que aplica la regla de no sobrescritura
- [ ] 2.3 `ClienteFactory` para clientes nuevos

## 3. Aplicación y API
- [ ] 3.1 `AltaClienteService` con el algoritmo de la sección 5.7 (pasos 1, 3, 4, 5 y 6; el CUIL llega en el change 04)
- [ ] 3.2 `POST /clientes` (201 con Location / 200), `GET /clientes/{id}` con ETag, `GET /clientes` paginado
- [ ] 3.3 DTOs con Bean Validation y documentación OpenAPI

## 4. Tests
- [ ] 4.1 Unitarios de `Telefono` con la tabla de la sección 5.6
- [ ] 4.2 Unitarios de `Cliente.registrarRecontacto`
- [ ] 4.3 Integración (Testcontainers): todos los escenarios del spec, incluido el de concurrencia
````

### 6.3 `clientes-03-edicion-y-baja`

**proposal.md**

````markdown
# Proposal: Edición y baja de clientes

## Why
El telemarketer necesita corregir los datos del cliente sin pisar los cambios de un compañero,
y la Ley 25.326 exige poder suprimir los datos personales cuando el titular lo pide. Además,
`leads` guarda una copia de nombre y teléfono que tiene que enterarse de esos cambios.

## What Changes
- `PATCH /clientes/{id}` con control de concurrencia optimista (`If-Match` / `ETag`).
- `DELETE /clientes/{id}` como anonimización idempotente.
- Eventos de integración `ClienteActualizado` y `ClienteAnonimizado`, sin datos personales,
  publicados después del commit.

## Impact
- Specs: `clientes-gestion` (modificada con nuevos requisitos), `clientes-eventos` (nueva).
- Contracts: records `ClienteActualizado` y `ClienteAnonimizado`.
- leads: agrega un consumidor que refresca su copia (AC-03).
````

**spec delta** → `openspec/changes/clientes-03-edicion-y-baja/specs/clientes-gestion/spec.md`

````markdown
## ADDED Requirements

### Requirement: Edición con control de concurrencia
El servicio MUST permitir modificar nombre, teléfono, email, provincia, localidad y la marca de revisión de un cliente
mediante `PATCH /clientes/{id}`, exigiendo el header `If-Match` con la versión vigente.
Los campos ausentes o nulos MUST NOT modificarse.

#### Scenario: Edición exitosa
- **WHEN** se envía `PATCH /clientes/{id}` con `If-Match: W/"3"` y la versión actual es 3
- **THEN** responde 200 con la ficha actualizada y `ETag: W/"4"`

#### Scenario: Versión desactualizada
- **WHEN** se envía `PATCH /clientes/{id}` con `If-Match: W/"2"` y la versión actual es 3
- **THEN** responde 412 con `codigo` = `VERSION_DESACTUALIZADA` y el cliente no cambia

#### Scenario: Falta If-Match
- **WHEN** se envía `PATCH /clientes/{id}` sin header `If-Match`
- **THEN** responde 428 con `codigo` = `PRECONDICION_REQUERIDA`

#### Scenario: Teléfono de otro cliente
- **WHEN** se intenta cambiar el teléfono por uno que pertenece a otro cliente activo
- **THEN** responde 409 con `codigo` = `TELEFONO_EN_USO`

### Requirement: Baja por anonimización
El servicio MUST atender `DELETE /clientes/{id}` anonimizando al cliente: MUST eliminar nombre, teléfonos, email,
localidad y CUIL, revocar sus consentimientos vigentes y borrar sus datos asociados, conservando el ID,
la provincia, el canal de origen y las fechas. La operación MUST ser idempotente.

#### Scenario: Anonimización
- **WHEN** se envía `DELETE /clientes/{id}` de un cliente activo
- **THEN** responde 204
- **AND** `GET /clientes/{id}` responde 410 con `codigo` = `CLIENTE_ANONIMIZADO`

#### Scenario: Baja repetida
- **WHEN** se envía `DELETE /clientes/{id}` de un cliente ya anonimizado
- **THEN** responde 204 y no publica un nuevo evento

#### Scenario: El teléfono queda libre
- **WHEN** se anonimiza un cliente con teléfono `+5491112345678`
- **AND** luego se envía `POST /clientes` con ese teléfono
- **THEN** se crea un cliente nuevo (201)

#### Scenario: Cliente inexistente
- **WHEN** se envía `DELETE /clientes/{id}` con un ID que nunca existió
- **THEN** responde 404 con `codigo` = `CLIENTE_NO_ENCONTRADO`
````

**spec delta** → `openspec/changes/clientes-03-edicion-y-baja/specs/clientes-eventos/spec.md`

````markdown
## ADDED Requirements

### Requirement: Publicación de ClienteActualizado
Cuando una edición modifica al menos un campo, el servicio MUST publicar `ClienteActualizado` en el tópico
`cliente-actualizado` con clave `clienteId`, después de confirmar la transacción, indicando los nombres de los campos modificados.

#### Scenario: Cambio de nombre y teléfono
- **WHEN** un PATCH modifica nombre y teléfono
- **THEN** se publica `ClienteActualizado` con `campos` = [`NOMBRE`, `TELEFONO`]

#### Scenario: Edición sin cambios reales
- **WHEN** un PATCH envía los mismos valores que ya tenía el cliente
- **THEN** no se publica ningún evento y la versión no cambia

#### Scenario: Transacción fallida
- **WHEN** la edición falla y la transacción se revierte
- **THEN** no se publica ningún evento

### Requirement: Publicación de ClienteAnonimizado
Al anonimizar un cliente, el servicio MUST publicar `ClienteAnonimizado` en el tópico `cliente-anonimizado` con clave `clienteId`.

#### Scenario: Evento de anonimización
- **WHEN** se anonimiza un cliente activo
- **THEN** se publica un `ClienteAnonimizado` con su `clienteId`

### Requirement: Eventos sin datos personales
Los eventos que publica el servicio MUST usar el sobre común de integración (eventId, eventType, version, occurredAt, correlationId, payload)
y MUST NOT incluir nombre, teléfono, email ni CUIL.

#### Scenario: Revisión del payload
- **WHEN** se publica cualquier evento del servicio
- **THEN** el payload contiene solo identificadores y nombres de campos
- **AND** el `correlationId` es el del request que lo originó
````

**tasks.md**

````markdown
# Tasks

## 1. Contracts
- [ ] 1.1 Records `ClienteActualizado` y `ClienteAnonimizado` (con sobre común) en `contracts`
- [ ] 1.2 Crear tópicos `cliente-actualizado` y `cliente-anonimizado` (script de infra o `NewTopic` beans)

## 2. Dominio y aplicación
- [ ] 2.1 `Cliente.editar(...)` que devuelve los campos modificados y registra `ClienteActualizadoEvento`
- [ ] 2.2 `Cliente.anonimizar()` idempotente que registra `ClienteAnonimizadoEvento`
- [ ] 2.3 `EdicionClienteService` (If-Match vs `@Version`; 409 por teléfono en uso)
- [ ] 2.4 `BajaClienteService` (anonimización; borrado de datos asociados a medida que existan en changes siguientes)
- [ ] 2.5 `ClienteEventosListener` con `@TransactionalEventListener(phase = AFTER_COMMIT)` → `ClienteEventosPublisher`

## 3. API
- [ ] 3.1 `PATCH /clientes/{id}` con `If-Match` y nuevo `ETag`
- [ ] 3.2 `DELETE /clientes/{id}`; `GET` de un anonimizado → 410

## 4. Tests
- [ ] 4.1 Unitarios de `editar` y `anonimizar`
- [ ] 4.2 Integración con Testcontainers (PostgreSQL + Kafka): escenarios del spec, verificando los mensajes en los tópicos
- [ ] 4.3 Test de que un rollback no publica eventos
````

### 6.4 `clientes-04-cuil-y-consentimientos`

> **Nota (change 04, 2026-10-09):** implementado con alcance "MVP+" acordado contra `contratos-backend.md` v1.1:
> entra lo caro de cambiar después (cifrado AES-GCM, índice ciego HMAC, tabla `consentimiento`, forma del request
> con `leads`) y los endpoints aditivos pasan al backlog (§8, "privacidad ampliada"). Diferencias con el texto de
> abajo: migración `V3__cuil_y_consentimiento.sql` (no V2); el CUIL y el consentimiento llegan solo por
> `POST /clientes` desde `leads` (no por el front, AC-01 no se adoptó) con `consentimiento: true` booleano; solo la
> finalidad `PRECALIFICACION_CREDITICIA`; deduplicación CUIL → email (§5.7); no hay `PUT /clientes/{id}/cuil`,
> `POST /clientes/busqueda-por-cuil` ni `POST/DELETE .../consentimientos`; la anonimización revoca (no borra) los
> consentimientos; el PATCH suma `telefonoAlternativo` y `requiereRevision: false`. Ver el change archivado.

**proposal.md**

````markdown
# Proposal: CUIL y consentimientos

## Why
Para precalificar crediticiamente al prospecto hace falta su CUIL, que es un dato personal protegido por la
Ley 25.326. El servicio tiene que validarlo, guardarlo cifrado, no exponerlo nunca completo y usarlo solo
para las finalidades que el titular consintió.

## What Changes
- Value object `Cuil` con validación de prefijo y dígito verificador.
- CUIL opcional en `POST /clientes` (con deduplicación por CUIL) y `PUT /clientes/{id}/cuil`.
- Cifrado AES-GCM e índice ciego HMAC; enmascarado en las respuestas.
- `POST /clientes/busqueda-por-cuil` (CUIL en el cuerpo, nunca en la URL).
- Consentimientos por finalidad: otorgar, listar, revocar.
- Migración `V2__cuil_y_consentimiento.sql`.

## Impact
- Specs: `clientes-privacidad` (nueva).
- Frontend: el paso 2 del preformulario envía CUIL y consentimiento a `clientes` (AC-01).
````

**spec delta** → `openspec/changes/clientes-04-cuil-y-consentimientos/specs/clientes-privacidad/spec.md`

````markdown
## ADDED Requirements

### Requirement: Validación del CUIL
El servicio MUST aceptar el CUIL con o sin guiones y MUST rechazarlo si no tiene 11 dígitos,
si su prefijo no es de persona humana (20, 23, 24 o 27) o si su dígito verificador por módulo 11 no es correcto.

#### Scenario: CUIL válido con guiones
- **WHEN** se informa el CUIL `20-12345678-6`
- **THEN** se acepta y se guarda normalizado como `20123456786`

#### Scenario: Dígito verificador incorrecto
- **WHEN** se informa el CUIL `20-12345678-5`
- **THEN** responde 422 con `codigo` = `CUIL_INVALIDO`

#### Scenario: Prefijo de persona jurídica
- **WHEN** se informa el CUIL `30-71234567-1`
- **THEN** responde 422 con `codigo` = `CUIL_INVALIDO`

### Requirement: El CUIL requiere consentimiento de precalificación
El servicio MUST NOT registrar un CUIL si en el mismo request no se otorga, o no existe vigente,
el consentimiento con finalidad `PRECALIFICACION_CREDITICIA`.

#### Scenario: CUIL sin consentimiento
- **WHEN** se envía un CUIL válido sin consentimiento de precalificación
- **THEN** responde 422 con `codigo` = `CONSENTIMIENTO_REQUERIDO` y no se guarda nada

### Requirement: Alta con CUIL y deduplicación por CUIL
En `POST /clientes`, cuando se informa un CUIL que pertenece a un cliente activo, el servicio MUST devolver ese cliente
aunque el teléfono sea distinto, guardando el teléfono informado como `telefonoAlternativo`.
Cuando el alta coincide por teléfono con un cliente que tiene otro CUIL, MUST NOT reemplazarlo y MUST marcar al cliente
con `requiereRevision` = true y motivo `CUIL_DISTINTO`.

#### Scenario: Mismo CUIL, teléfono nuevo
- **WHEN** existe un cliente con CUIL `27-30111222-5` y teléfono `+5491112345678`
- **AND** se envía `POST /clientes` con ese CUIL y teléfono `11 9999-0000`
- **THEN** responde 200 con el mismo `clienteId` y `coincidencia` = `CUIL`
- **AND** el cliente conserva su teléfono principal y queda con `telefonoAlternativo` = `+5491199990000`

#### Scenario: Mismo teléfono, otro CUIL
- **WHEN** existe un cliente con teléfono `+5491112345678` y CUIL `27-30111222-5`
- **AND** se envía `POST /clientes` con ese teléfono y CUIL `20-12345678-6`
- **THEN** responde 200 con el mismo `clienteId`
- **AND** el CUIL registrado sigue siendo el original y el cliente queda con `requiereRevision` = true

### Requirement: Registro y reemplazo del CUIL
El servicio MUST permitir registrar o reemplazar el CUIL de un cliente con `PUT /clientes/{id}/cuil`, de forma idempotente,
y MUST rechazar un CUIL que pertenezca a otro cliente activo. Reemplazar el CUIL MUST invalidar la situación crediticia guardada.

#### Scenario: Registro exitoso
- **WHEN** se envía `PUT /clientes/{id}/cuil` con un CUIL válido y consentimiento de precalificación
- **THEN** responde 200 con el CUIL enmascarado

#### Scenario: Repetición idempotente
- **WHEN** se envía dos veces el mismo `PUT /clientes/{id}/cuil`
- **THEN** ambas respuestas son 200 y el cliente queda igual

#### Scenario: CUIL de otro cliente
- **WHEN** se envía `PUT /clientes/{id}/cuil` con un CUIL que pertenece a otro cliente activo
- **THEN** responde 409 con `codigo` = `CUIL_EN_USO`

### Requirement: Protección del CUIL
El servicio MUST guardar el CUIL cifrado y MUST NOT devolverlo completo en ninguna respuesta;
en las respuestas MUST mostrarlo con el formato `XX-XXXX` seguido de los últimos 4 dígitos del DNI y `-X`.

#### Scenario: CUIL en la base
- **WHEN** se registra el CUIL `20-12345678-6`
- **THEN** ninguna columna de la tabla `cliente` contiene el texto `20123456786`

#### Scenario: CUIL en la ficha
- **WHEN** se consulta la ficha de un cliente con CUIL `20-12345678-6`
- **THEN** el campo `cuil` vale `XX-XXXX5678-X`

### Requirement: Búsqueda por CUIL sin exponerlo en la URL
El servicio MUST permitir buscar un cliente activo por CUIL únicamente mediante `POST /clientes/busqueda-por-cuil` con el CUIL en el cuerpo.

#### Scenario: Cliente encontrado
- **WHEN** se envía `POST /clientes/busqueda-por-cuil` con un CUIL registrado
- **THEN** responde 200 con la ficha del cliente

#### Scenario: Cliente no encontrado
- **WHEN** se envía un CUIL válido que no está registrado
- **THEN** responde 404 con `codigo` = `CLIENTE_NO_ENCONTRADO`

### Requirement: Consentimientos por finalidad
El servicio MUST registrar consentimientos con finalidad (`PRECALIFICACION_CREDITICIA` o `CONTACTO_COMERCIAL`), versión del texto aceptado,
origen y fecha; MUST permitir consultarlos y revocarlos, y MUST mantener como máximo un consentimiento vigente por finalidad.

#### Scenario: Otorgar un consentimiento ya vigente
- **WHEN** se envía `POST /clientes/{id}/consentimientos` para una finalidad que ya tiene un consentimiento vigente
- **THEN** responde 200 con el consentimiento existente y no se crea otro

#### Scenario: Revocar consentimiento de precalificación
- **WHEN** se envía `DELETE /clientes/{id}/consentimientos/PRECALIFICACION_CREDITICIA`
- **THEN** responde 204, el consentimiento queda con fecha de revocación
- **AND** se elimina la situación crediticia guardada del cliente

#### Scenario: Historial de consentimientos
- **WHEN** se consulta `GET /clientes/{id}/consentimientos` de un cliente que otorgó y luego revocó un consentimiento
- **THEN** la respuesta incluye ese consentimiento con `otorgadoEn` y `revocadoEn`
````

**tasks.md**

````markdown
# Tasks

## 1. Persistencia y seguridad
- [ ] 1.1 Migración `V2__cuil_y_consentimiento.sql` (sección 5.2)
- [ ] 1.2 Puerto `CifradorCuil` y adaptador `AesGcmCifradorCuil` (AES-256-GCM + HMAC-SHA256, claves por variable de entorno)
- [ ] 1.3 Entidad y repositorio de `Consentimiento`

## 2. Dominio
- [ ] 2.1 Value object `Cuil` (normalización, prefijo, módulo 11, enmascarado)
- [ ] 2.2 `Cliente.registrarCuil(...)`, `marcarRevision(...)`, teléfono alternativo
- [ ] 2.3 Reglas de consentimiento: uno vigente por finalidad, revocación

## 3. Aplicación y API
- [ ] 3.1 Extender `AltaClienteService` con los pasos 1 a 3 de la sección 5.7
- [ ] 3.2 `PUT /clientes/{id}/cuil`, `POST /clientes/busqueda-por-cuil`
- [ ] 3.3 `GET/POST /clientes/{id}/consentimientos`, `DELETE /clientes/{id}/consentimientos/{finalidad}`
- [ ] 3.4 Agregar `cuil` enmascarado y `consentimientosVigentes` a la ficha
- [ ] 3.5 Extender la anonimización: borrar CUIL y revocar consentimientos

## 4. Tests
- [ ] 4.1 Unitarios de `Cuil` con la tabla de la sección 5.6
- [ ] 4.2 Unitarios del cifrador (ida y vuelta, IV distinto en cada cifrado, hash estable)
- [ ] 4.3 Integración: escenarios del spec, incluida la verificación por SQL de que el CUIL no está en claro
- [ ] 4.4 Ampliar el test de logs con el flujo de alta con CUIL
````

### 6.5 `clientes-05-situacion-crediticia`

**proposal.md**

````markdown
# Proposal: Situación crediticia

## Why
El vendedor hoy no recibe el Veraz ni la situación crediticia del prospecto y tiene que rellamar.
`calificacion` necesita la situación crediticia para aplicar ReglaFinanciacion, y no puede quedar
bloqueada si la fuente externa está lenta o caída.

## What Changes
- `GET /clientes/{id}/situacion-crediticia` con estado explícito (AC-05).
- Puerto `SituacionCrediticiaPort` con adaptador simulado determinístico, seleccionable por configuración.
- Caché en base con vigencia configurable; los resultados no disponibles no se cachean.
- Timeout y circuit breaker con Resilience4j.
- Migración `V3__situacion_crediticia.sql`.

## Impact
- Specs: `clientes-situacion-crediticia` (nueva).
- Contracts: enum `EstadoSituacionCrediticia`.
- calificacion: adapta su cliente REST y el mapeo de puntos (AC-05).
- Segunda Parte: solo se agrega `BcraSoapSituacionAdapter`.
````

**spec delta** → `openspec/changes/clientes-05-situacion-crediticia/specs/clientes-situacion-crediticia/spec.md`

````markdown
## ADDED Requirements

### Requirement: Consulta de la situación crediticia
El servicio MUST responder `GET /clientes/{id}/situacion-crediticia` con un `estado`
(`CON_DATOS`, `SIN_REGISTROS`, `SIN_CUIL`, `SIN_CONSENTIMIENTO` o `NO_DISPONIBLE`) y, solo cuando el estado es `CON_DATOS`,
una `situacion` de 1 a 6. La respuesta MUST NOT incluir el CUIL.

#### Scenario: Cliente sin CUIL
- **WHEN** se consulta la situación de un cliente que no tiene CUIL
- **THEN** responde 200 con `estado` = `SIN_CUIL` y `situacion` nula, sin consultar la fuente

#### Scenario: Sin consentimiento vigente
- **WHEN** se consulta la situación de un cliente con CUIL pero sin consentimiento `PRECALIFICACION_CREDITICIA` vigente
- **THEN** responde 200 con `estado` = `SIN_CONSENTIMIENTO`, sin consultar la fuente

#### Scenario: Consulta con datos
- **WHEN** se consulta la situación de un cliente con CUIL `27-30111222-5` y consentimiento vigente, usando el adaptador simulado
- **THEN** responde 200 con `estado` = `CON_DATOS`, `situacion` = 1 y `fuente` = `SIMULADA`

#### Scenario: Sin registros en la fuente
- **WHEN** se consulta la situación de un cliente con CUIL `20-28555449-8`, usando el adaptador simulado
- **THEN** responde 200 con `estado` = `SIN_REGISTROS` y `situacion` nula

### Requirement: Caché con vigencia
El servicio MUST guardar los resultados `CON_DATOS` y `SIN_REGISTROS` durante la vigencia configurada (por defecto 30 días)
y MUST responder desde esa caché sin consultar la fuente mientras esté vigente. MUST NOT cachear `NO_DISPONIBLE`.

#### Scenario: Resultado vigente
- **WHEN** se consulta dos veces la situación del mismo cliente dentro de la vigencia
- **THEN** la fuente se consulta una sola vez y ambas respuestas son iguales

#### Scenario: Resultado vencido
- **WHEN** se consulta la situación de un cliente cuyo resultado guardado está vencido
- **THEN** se vuelve a consultar la fuente y se actualiza el resultado guardado

### Requirement: Degradación ante fallas de la fuente
Si la fuente no responde dentro del timeout configurado, falla o el circuit breaker está abierto, el servicio MUST responder 200 con
`estado` = `NO_DISPONIBLE` y MUST NOT responder con un error 5xx por esa causa.

#### Scenario: Fuente caída
- **WHEN** el adaptador simulado está configurado con `forzar-no-disponible` = true
- **THEN** la consulta responde 200 con `estado` = `NO_DISPONIBLE`
- **AND** al restablecerse la fuente, la siguiente consulta la vuelve a intentar

#### Scenario: Fuente lenta
- **WHEN** el adaptador simulado tiene una demora mayor que el timeout
- **THEN** la consulta responde 200 con `estado` = `NO_DISPONIBLE` en un tiempo cercano al timeout

### Requirement: Adaptador simulado determinístico
El adaptador simulado MUST resolver la situación según el último dígito del DNI contenido en el CUIL:
0 a 4 → situación 1; 5 o 6 → situación 2; 7 → situación 3; 8 → situación 5; 9 → sin registros.

#### Scenario: Situación negativa
- **WHEN** se consulta con el adaptador simulado un cliente con CUIL `20-12345678-6`
- **THEN** el resultado es `CON_DATOS` con `situacion` = 5

### Requirement: Selección del adaptador por configuración
El servicio MUST elegir la implementación de `SituacionCrediticiaPort` según `clientes.situacion-crediticia.modo`
sin cambios en el dominio ni en la aplicación.

#### Scenario: Modo simulado
- **WHEN** el servicio arranca con `modo` = `simulado`
- **THEN** las consultas usan el adaptador simulado y registran `fuente` = `SIMULADA`
````

**tasks.md**

````markdown
# Tasks

## 1. Contracts y persistencia
- [ ] 1.1 Enum `EstadoSituacionCrediticia` en `contracts` y DTO de respuesta compartido con calificacion
- [ ] 1.2 Migración `V3__situacion_crediticia.sql`; entidad y repositorio

## 2. Dominio e infraestructura
- [ ] 2.1 Puerto `SituacionCrediticiaPort` (recibe CUIL normalizado, devuelve estado, situación y período)
- [ ] 2.2 `SimuladaSituacionAdapter` con la tabla de la sección 5.10, demora y falla configurables
- [ ] 2.3 Selección por `@ConditionalOnProperty` según `modo`
- [ ] 2.4 Timeout y circuit breaker de Resilience4j alrededor del puerto

## 3. Aplicación y API
- [ ] 3.1 `SituacionCrediticiaService`: orden de evaluación SIN_CUIL → SIN_CONSENTIMIENTO → caché vigente → fuente
- [ ] 3.2 `GET /clientes/{id}/situacion-crediticia`
- [ ] 3.3 Integrar con revocación de consentimiento, reemplazo de CUIL y anonimización (borrar caché)

## 4. Tests
- [ ] 4.1 Unitarios del adaptador simulado (tabla completa)
- [ ] 4.2 Unitarios del servicio con un `Clock` fijo para probar la vigencia
- [ ] 4.3 Integración: escenarios del spec, incluidos fuente caída y fuente lenta
- [ ] 4.4 Avisar al responsable de calificacion el contrato final para su cliente REST
````

### 6.6 `clientes-06-notas`

**proposal.md**

````markdown
# Proposal: Notas del cliente

## Why
El cliente pidió que el vendedor no tenga que rellamar al prospecto. Las notas permiten que lo que
releva el telemarketer en cada contacto quede en la ficha y se acumule entre distintos negocios.

## What Changes
- `POST /clientes/{id}/notas` y `GET /clientes/{id}/notas` paginado.
- Notas inmutables; autor tomado del header `X-Usuario` hasta que exista autenticación.
- Las notas se borran al anonimizar.
- Migración `V4__nota.sql`.

## Impact
- Specs: `clientes-seguimiento` (nueva).
- Frontend: sección de notas en la ficha (opcional para la Primera Parte).
````

**spec delta** → `openspec/changes/clientes-06-notas/specs/clientes-seguimiento/spec.md`

````markdown
## ADDED Requirements

### Requirement: Registro de notas
El servicio MUST permitir agregar a un cliente activo una nota de 1 a 1000 caracteres, registrando como autor
el valor del header `X-Usuario` (o `sin-usuario` si no viene) y la fecha de creación. Las notas MUST NOT poder editarse ni borrarse por la API.

#### Scenario: Nota agregada
- **WHEN** se envía `POST /clientes/{id}/notas` con texto "Tiene un Gol 2015 para permutar, quiere ver la unidad el sábado" y `X-Usuario: tm-ana`
- **THEN** responde 201 con la nota, su autor `tm-ana` y la fecha

#### Scenario: Nota vacía
- **WHEN** se envía una nota con texto vacío
- **THEN** responde 422 con `codigo` = `VALIDACION`

### Requirement: Consulta de notas
El servicio MUST listar las notas de un cliente paginadas, de la más reciente a la más antigua.

#### Scenario: Orden de las notas
- **WHEN** un cliente tiene tres notas creadas en distintos momentos
- **THEN** `GET /clientes/{id}/notas` las devuelve de la más nueva a la más vieja

### Requirement: Notas y anonimización
Al anonimizar un cliente, el servicio MUST eliminar todas sus notas.

#### Scenario: Notas de un cliente anonimizado
- **WHEN** se anonimiza un cliente con notas
- **THEN** la tabla `nota` no conserva registros de ese cliente
````

**tasks.md**

````markdown
# Tasks

- [ ] 1.1 Migración `V4__nota.sql`; entidad y repositorio
- [ ] 1.2 `NotaService` y endpoints `POST`/`GET /clientes/{id}/notas`
- [ ] 1.3 Extender `BajaClienteService` para borrar notas
- [ ] 1.4 Tests de integración de los escenarios del spec
````

### 6.7 `clientes-07-preferencias-e-interesados`

**proposal.md**

````markdown
# Proposal: Preferencias de vehículo e interesados

## Why
Un prospecto que no califica para la unidad que consultó puede ser un buen cliente para otra más barata
o de otro tipo. Guardar qué busca cada cliente permite, cuando entra una unidad, saber a quién ofrecérsela
y reactivar contactos que hoy se pierden. Es la base de la reactivación automática de la Segunda Parte.

## What Changes
- `PUT/GET /clientes/{id}/preferencias` (hasta 5 preferencias por cliente).
- `GET /clientes/interesados` que cruza tipo, marca, precio y año de una unidad con las preferencias
  de clientes activos con consentimiento `CONTACTO_COMERCIAL` vigente.
- Tolerancia de precio configurable (margen de negociación).
- Migración `V5__preferencia_vehiculo.sql`.

## Impact
- Specs: `clientes-seguimiento` (se agregan requisitos).
- Contracts: enum `TipoVehiculo` (AC-06); vehiculos agrega el atributo `tipo`.
````

**spec delta** → `openspec/changes/clientes-07-preferencias-e-interesados/specs/clientes-seguimiento/spec.md`

````markdown
## ADDED Requirements

### Requirement: Preferencias de vehículo
El servicio MUST permitir reemplazar y consultar las preferencias de vehículo de un cliente activo, hasta 5 por cliente.
Cada preferencia MUST tener tipo de vehículo y MAY tener marca, precio máximo y año mínimo.

#### Scenario: Reemplazo de preferencias
- **WHEN** se envía `PUT /clientes/{id}/preferencias` con dos preferencias
- **THEN** responde 200 y el cliente queda con exactamente esas dos preferencias

#### Scenario: Demasiadas preferencias
- **WHEN** se envían seis preferencias
- **THEN** responde 422 con `codigo` = `VALIDACION`

### Requirement: Búsqueda de clientes interesados
El servicio MUST devolver, paginados y ordenados por `ultimoContactoEn` descendente, los clientes activos con consentimiento
`CONTACTO_COMERCIAL` vigente que tengan al menos una preferencia compatible con la unidad consultada. Una preferencia es compatible si
el tipo coincide, la marca es nula o coincide sin distinguir mayúsculas, el precio máximo es nulo o el precio de la unidad no lo supera
en más que la tolerancia configurada, y el año mínimo es nulo o el año de la unidad es mayor o igual.

#### Scenario: Unidad dentro de la tolerancia
- **WHEN** un cliente prefiere AUTO con precio máximo 8.000.000 y la tolerancia es 10%
- **AND** se consulta `GET /clientes/interesados?tipoVehiculo=AUTO&precio=8500000&anio=2018`
- **THEN** el cliente aparece en el resultado

#### Scenario: Unidad fuera de la tolerancia
- **WHEN** existe el mismo cliente y se consulta con `precio=9000000`
- **THEN** el cliente no aparece en el resultado

#### Scenario: Sin consentimiento comercial
- **WHEN** un cliente con preferencia compatible revocó el consentimiento `CONTACTO_COMERCIAL`
- **THEN** no aparece en el resultado

### Requirement: Preferencias y anonimización
Al anonimizar un cliente, el servicio MUST eliminar sus preferencias.

#### Scenario: Preferencias de un cliente anonimizado
- **WHEN** se anonimiza un cliente con preferencias
- **THEN** la tabla `preferencia_vehiculo` no conserva registros de ese cliente
````

**tasks.md**

````markdown
# Tasks

- [ ] 1.1 Enum `TipoVehiculo` en `contracts` (coordinar con vehiculos; si no está listo, enum local con TODO)
- [ ] 1.2 Migración `V5__preferencia_vehiculo.sql`; entidad y repositorio
- [ ] 1.3 `PreferenciaService` y endpoints `PUT`/`GET /clientes/{id}/preferencias`
- [ ] 1.4 `InteresadosService` con consulta JPQL o Criteria y tolerancia desde configuración
- [ ] 1.5 `GET /clientes/interesados`
- [ ] 1.6 Extender `BajaClienteService` para borrar preferencias
- [ ] 1.7 Tests de integración de los escenarios del spec
````

---

## 7. Datos de prueba de referencia

Todos ficticios. Sirven para tests, seeds del perfil `local` y la demo.

| Nombre | Teléfono | Provincia | CUIL | Situación simulada | Para probar |
| --- | --- | --- | --- | --- | --- |
| Martina López | 11 1234-5678 | BUENOS_AIRES | 27-30111222-5 | 1 | Flujo feliz, Muy potable con anticipo |
| Diego Ferreyra | 0351 15 412-3456 | CORDOBA | 20-41000006-8 | 2 | Regla de ubicación |
| Carla Benítez | 11 2233-4455 | CABA | 20-35000007-1 | 3 | Situación negativa |
| Jorge Paz | 11 5566-7788 | BUENOS_AIRES | 20-12345678-6 | 5 | Situación negativa, 100% financiado |
| Sofía Ríos | 11 3344-5566 | BUENOS_AIRES | 20-28555449-8 | Sin registros | Financiación sin datos |
| Lucas Gómez | 11 4455-6677 | CABA | — | Sin CUIL | Prospecto que no completa el paso 2 |

Combinados con los casos de la sección 6.3 del documento de arquitectura (precio de unidad 10.000.000, zona Buenos Aires y CABA), cubren los nueve escenarios de calificación de punta a punta.

---

## 8. Backlog para próximas entregas

| ID | Funcionalidad | Entrega | Notas |
| --- | --- | --- | --- |
| clientes-08 | **Reactivación por stock (RFC-11).** Consumir `vehiculo-ingresado` y `vehiculo-precio-actualizado` de `vehiculos`, buscar interesados y publicar `OportunidadDetectada` con `vehiculoId` y la lista de `clienteId`. `leads` crea leads de reactivación en la cola. | Segunda | Demuestra integración asincrónica en ambos sentidos. Requiere idempotencia del consumidor (tabla de `eventId` procesados) y que `vehiculos` publique eventos. |
| clientes-09 | **Adaptador SOAP de precalificación (RFC-12).** `BcraSoapSituacionAdapter` que consume el WSDL del servicio `precalificacion`. | Segunda | Solo infraestructura; los specs del change 05 no cambian salvo `fuente` = `BCRA`. |
| clientes-10 | **Outbox.** Reemplazar la publicación AFTER_COMMIT por tabla outbox y relay. | Integrador | Alineado con AD-07. |
| clientes-11 | **Auditoría de accesos al CUIL.** Registrar quién y cuándo usó el CUIL (consultas a la fuente, búsquedas por CUIL). | Integrador | Depende de la autenticación. |
| clientes-12 | **Fusión de duplicados.** Resolver los clientes marcados con `requiereRevision`: unir fichas, notas y preferencias, y publicar `ClientesFusionados`. | Post-TP | Útil en producción; costoso para el TP. |
| clientes-13 | **Etiquetas y segmentos** (referido, recompra, flota). | Post-TP | Base para campañas. |
| clientes-14 | **Exportación de datos del titular** (derecho de acceso, Ley 25.326). | Post-TP | Simple una vez que existe la ficha completa. |
| clientes-15 | **Privacidad ampliada** (diferido del change 04). `PUT /clientes/{id}/cuil` (registrar o corregir el CUIL, invalidando la situación crediticia), `POST /clientes/busqueda-por-cuil` (CUIL en el cuerpo), `GET/POST/DELETE /clientes/{id}/consentimientos` (otorgar y revocar; revocar precalificación borra el CUIL), finalidad `CONTACTO_COMERCIAL` (la necesita el 07) y `email_alternativo` para el email descartado en una coincidencia por CUIL. | Segunda | Aditivo: el modelo de datos del 04 ya lo soporta, sin migrar datos. La revocación conviene sumarla con el 05, cuando tiene efecto observable. |

---

## 9. Preguntas abiertas para el equipo

1. ¿Se adopta AC-01 (el frontend registra al cliente y después el lead)? Si no, `leads` tiene que reenviar CUIL y consentimientos a `clientes` sin guardarlos ni loguearlos.
2. ¿El responsable de `leads` acepta consumir `cliente-actualizado` y `cliente-anonimizado` (AC-03)?
3. ¿`vehiculos` agrega `tipo` y los enums compartidos van en `contracts` (AC-06)?
4. ¿`calificacion` adopta el estado explícito de situación crediticia y el mapeo de puntos de AC-05?
5. ¿Quién redacta el texto de los consentimientos (finalidad declarada) y cómo se versiona? El servicio solo guarda la versión (`v1`).
6. ¿Vigencia de 30 días para la caché de situación crediticia y tolerancia de 10% para interesados? Ambas son configurables.
7. Para clientes anonimizados, ¿410 Gone (propuesta) o 404?
