# ClientMicroservice — servicio `clientes`

Microservicio `clientes` del CRM de calificación de leads de una agencia de vehículos usados (TPO de Desarrollo de
Aplicaciones II, UADE). Es la **ficha única del comprador**: guarda los datos de la persona que sobreviven a cada
negocio puntual, evita fichas duplicadas cuando la misma persona escribe por varios canales, protege el CUIL y responde
a los demás servicios lo que necesitan saber del cliente.

> Regla para decidir qué va acá: si el dato describe a la persona y sobrevive a un negocio puntual, va en `clientes`;
> si describe una operación concreta (unidad elegida, anticipo, permuta), va en `leads`.

## Índice

1. [Qué hace y qué no hace](#1-qué-hace-y-qué-no-hace)
2. [Arquitectura](#2-arquitectura)
3. [Cómo funciona](#3-cómo-funciona)
4. [Entradas y salidas](#4-entradas-y-salidas)
5. [API REST](#5-api-rest)
6. [Eventos (Kafka)](#6-eventos-kafka)
7. [Errores](#7-errores)
8. [Modelo de datos](#8-modelo-de-datos)
9. [Seguridad y protección de datos](#9-seguridad-y-protección-de-datos)
10. [Patrones de diseño](#10-patrones-de-diseño)
11. [Configuración](#11-configuración)
12. [Cómo levantarlo y probarlo](#12-cómo-levantarlo-y-probarlo)
13. [Tests](#13-tests)
14. [Limitaciones conocidas y próximos pasos](#14-limitaciones-conocidas-y-próximos-pasos)
15. [Documentación relacionada](#15-documentación-relacionada)

---

## 1. Qué hace y qué no hace

| Hace | No hace |
| --- | --- |
| Alta de clientes **idempotente con deduplicación** por CUIL y por email (la usa `leads`) | No maneja el ciclo de vida del negocio (eso es `leads`) |
| Ficha, búsqueda paginada y edición con **control de concurrencia optimista** | No calcula la calificación (eso es `calificacion`) |
| Marca **"no contactar"** y **anonimización** (derecho de supresión, Ley 25.326) | No guarda datos de la operación: unidad, anticipo, permuta |
| **CUIL cifrado** con consentimiento informado de precalificación | No expone el CUIL: nunca sale completo, ni en respuestas, eventos, URLs ni logs |
| **Situación crediticia** con caché, circuit breaker y estado explícito (la usa `calificacion`) | No tiene autenticación de usuarios (llega en una entrega posterior) |
| **Historial de calificaciones** de todos los leads del cliente, alimentado por `lead-calificado` | |
| Publica `ClienteActualizado` y `ClienteAnonimizado` para que `leads` mantenga sus copias | |

---

## 2. Arquitectura

### 2.1 Contexto: dónde está `clientes` en el sistema

```
                       +----------------------+
                       |        front         |
                       +----------+-----------+
                                  |  REST (directo a :8082 hasta que entre el gateway)
                                  |  ficha, búsqueda, edición, no contactar,
                                  |  anonimización, historial
                                  v
+-----------+  POST /clientes  +--+-------------------+  GET situacion-crediticia  +---------------+
|   leads   | ---------------> |      clientes        | <------------------------- | calificacion  |
| (Python)  |   X-Api-Key      |   (este servicio)    |        X-Api-Key           |               |
+-----+-----+                  +--+---------------+---+                            +-------+-------+
      ^                           |               ^                                        |
      |  cliente-actualizado      |               |  lead-calificado                       |
      |  cliente-anonimizado      v               |                                        |
      +---------------------- [ Kafka ] ----------+----------------------------------------+
                                  |
                                  v
                        +---------+---------+
                        |  PostgreSQL 16    |
                        |  base clientes_db |  (cada servicio es dueño de su base)
                        +-------------------+
```

- **REST** cuando el que llama necesita la respuesta: `leads` da de alta y recibe el `clienteId`, y `calificacion`
  consulta la situación crediticia.
- **Kafka** cuando solo hay que avisar que algo pasó: `clientes` avisa cambios de la ficha y `calificacion` avisa cada
  calificación.
- Ningún servicio lee la base de otro. Las relaciones entre servicios son por ID: `clienteId` y `leadId`.

### 2.2 Capas (arquitectura hexagonal)

```
   api  ------------------->  application  ------------------->  domain  <-------------------  infrastructure
   (controllers, DTOs,        (casos de uso, uno por              (agregado Cliente,              (JPA, Kafka, AES/HMAC,
    errores, interceptores)    servicio; listeners de              value objects, factory,          fuente simulada,
                               eventos de dominio)                 eventos y PUERTOS)               decorators)
```

- **El dominio no depende de nada externo:** ni de Spring Data, ni de Kafka, ni de clientes HTTP. Define **puertos**
  (interfaces) que la infraestructura implementa: `ClienteRepositorio`, `HistorialCalificaciones`, `CifradorCuil`,
  `PublicadorEventos`, `SituacionCrediticiaPort` y `CacheSituacionCrediticia`.
- **Hay un servicio de aplicación por caso de uso** (`AltaClienteService`, `EdicionClienteService`,
  `AnonimizacionClienteService`, etc.) en lugar de una fachada única.
- `config` reúne lo transversal: propiedades, `correlationId`, usuario, enmascarado de logs y OpenAPI.

### 2.3 Módulos y paquetes

| Módulo | Qué contiene |
| --- | --- |
| `contracts` | Tipos compartidos entre servicios, sin Spring: `Provincia` (24 jurisdicciones), `CanalOrigen`, el sobre `EventoIntegracion`, los eventos de `clientes` (`ClienteActualizado`, `ClienteAnonimizado`, `CampoCliente`) y `EstadoSituacionCrediticia`. |
| `clientes-service` | Spring Boot 3.5 / Java 21. Puerto interno 8082, base `clientes_db` (PostgreSQL 16 con Flyway). |

```text
com.uade.crm.clientes
├── api                     ClienteController, SituacionCrediticiaController, HistorialController
│   ├── dto                 requests y responses (records)
│   ├── error               ApiExceptionHandler (ProblemDetail) y catálogo CodigoError
│   └── seguridad           @RequiereClaveServicio(Consumidor) y ClaveServicioInterceptor
│                           (también @RequiereIfMatch / IfMatchInterceptor / VersionEtag en api)
├── application             AltaClienteService, ConsultaClienteService, EdicionClienteService, NoContactarService,
│   │                       AnonimizacionClienteService, SituacionCrediticiaService,
│   │                       RegistroCalificacionService, HistorialCalificacionesService
│   └── listener            ClienteEventosListener (AFTER_COMMIT -> Kafka),
│                           SituacionCrediticiaAnonimizacionListener y HistorialAnonimizacionListener (BEFORE_COMMIT)
├── domain
│   ├── model               Cliente (agregado), Consentimiento, SituacionCrediticia, CalificacionHistorica, ...
│   ├── valor               Email, Telefono, Cuil, CuilProtegido, NombreBusqueda, Enmascarador
│   ├── event               ClienteActualizadoEvento, ClienteAnonimizadoEvento (eventos de dominio)
│   ├── port                puertos que implementa la infraestructura
│   └── ClienteFactory
├── infrastructure
│   ├── persistence         entidades JPA y adaptadores de los puertos de persistencia
│   ├── messaging           PublicadorEventosKafka, LeadCalificadoListener, LeadCalificadoTraductor,
│   │                       KafkaConfig (tópicos), ConsumidorKafkaConfig (reintentos y DLT)
│   ├── crypto              AesGcmCifradorCuil
│   └── precalificacion     SimuladaSituacionAdapter y decorators de caché y resiliencia
└── config                  ClientesProperties, CorrelationIdFilter, UsuarioFilter, logs enmascarados, OpenAPI
```

### 2.4 Stack

Java 21, Spring Boot 3.5 (Web, Validation, Data JPA, Actuator), PostgreSQL 16 + Flyway, Apache Kafka 3.9 (KRaft) con
Spring for Apache Kafka, Resilience4j (circuit breaker), libphonenumber, springdoc-openapi, y para los tests JUnit 5,
Mockito, AssertJ, Awaitility y Testcontainers (PostgreSQL y Kafka reales). Docker Compose para el entorno de
laboratorio.

---

## 3. Cómo funciona

### 3.1 Alta con deduplicación (`POST /clientes`, la llama `leads`)

La misma persona puede escribir por Instagram, después por TikTok y después por WhatsApp. El alta busca primero por
CUIL y después por email (normalizado: sin espacios y en minúsculas). Si encuentra un cliente activo, **no crea otro**:
devuelve el existente con `200`.

```
 POST /clientes
      |
      v
 normalizar (ClienteFactory): email, teléfono E.164 móvil, nombre de búsqueda, CUIL validado y cifrado
      |
      v
 ¿hay CUIL y coincide con un cliente activo? --sí--> recontacto sobre ese cliente  (coincidencia = CUIL)
      | no                                            si el email es de OTRO cliente -> requiereRevision
      v                                                 (POSIBLE_DUPLICADO)
 ¿el email coincide con un cliente activo? ---sí--> recontacto  (coincidencia = EMAIL)
      | no                                            si ese cliente tiene OTRO CUIL -> requiereRevision
      v                                                 (CUIL_DISTINTO), sin guardar el CUIL nuevo
 crear cliente nuevo -> 201 + Location
```

- **El recontacto no pisa datos:** nunca cambia nombre, apellido, provincia, email ni un CUIL ya registrado.
  Solo completa campos vacíos, suma 1 a `contactosRegistrados`, actualiza `ultimoContactoEn` y `ultimoCanal`, y
  quita la marca "no contactar": la persona volvió a pedir contacto.
- **Altas simultáneas** de la misma persona: los índices únicos parciales (email y CUIL entre activos) y el bloqueo
  de la fila hacen que terminen sobre un solo cliente.
- **Reintentos seguros:** reenviar la misma alta no duplica nada.

### 3.2 Edición con control de concurrencia (`PATCH /clientes/{id}`)

La ficha se devuelve con `ETag` = versión. El `PATCH` exige `If-Match` con esa versión. Si otra persona, o un
recontacto, la cambió en el medio, responde `412` y hay que recargar. Solo los cambios reales generan un `ETag`
nuevo y publican `ClienteActualizado`, con la lista de campos modificados.

### 3.3 Anonimización (`POST /clientes/{id}/anonimizacion`)

Es una baja lógica que cumple el derecho de supresión sin romper las referencias que guardan otros servicios. Todo
ocurre **en una sola transacción**:

```
 AnonimizacionClienteService (fila del cliente FOR UPDATE)
   |- Cliente.anonimizar(): nombre/apellido = ANONIMIZADO; email, teléfonos, localidad y CUIL = null;
   |                        consentimientos revocados
   |- publica ClienteAnonimizadoEvento (evento de dominio)
   |     |- BEFORE_COMMIT: borra la situación crediticia guardada
   |     |- BEFORE_COMMIT: borra el historial de calificaciones
   |     '- AFTER_COMMIT : publica ClienteAnonimizado en Kafka
   '- commit  (si algún borrado falla, se revierte todo)
```

Después, la ficha responde `410 CLIENTE_ANONIMIZADO`, y el email y el CUIL quedan libres para un alta nueva. Se
conservan el ID, la provincia, los canales, el contador y las fechas: sirven para estadísticas sin identificar a nadie.

### 3.4 Situación crediticia (`GET /clientes/{id}/situacion-crediticia`, la llama `calificacion`)

```
 SituacionCrediticiaService  (404 / 410; sin CUIL -> SIN_CUIL sin consultar nada; descifra el CUIL en memoria)
      |
      v  SituacionCrediticiaPort
 SituacionCrediticiaConCache      lee la caché en base (30 días); guarda solo CON_DATOS / SIN_REGISTROS
      |
      v
 SituacionCrediticiaResiliente    circuit breaker de Resilience4j; cualquier falla -> NO_DISPONIBLE
      |
      v
 SimuladaSituacionAdapter         fuente determinística (en la Segunda Parte, el adaptador SOAP contra el BCRA)
```

- **Siempre `200`** para un cliente activo, con un `estado` explícito: `calificacion` nunca recibe un `5xx` por culpa
  de la fuente externa.
- **Cada fuente respeta el timeout** (2 s), y lo verifica un test de contrato que tendrá que pasar también el adaptador
  SOAP.
- **La caché vive en la base**, así que la comparten todas las réplicas. Un `NO_DISPONIBLE` no se guarda: la próxima
  consulta vuelve a intentar la fuente.

### 3.5 Historial de calificaciones (consume `lead-calificado`)

```
 lead-calificado (clave leadId)
      |
      v
 LeadCalificadoListener            correlationId en el MDC por mensaje
      |
      v
 LeadCalificadoTraductor           bytes -> CalificacionHistorica. Acepta JSON plano o sobre común;
      |                            fecha sin zona -> zona configurada. Inválido -> MensajeInvalidoException
      v
 RegistroCalificacionService       INSERT ... SELECT ... WHERE cliente ACTIVO FOR SHARE
      |                              ON CONFLICT (lead_id, origen, calculada_en) DO NOTHING
      |
      +-- 1 fila  -> registrada (INFO)
      '-- 0 filas -> descartada: duplicado (DEBUG) | cliente anonimizado (INFO) | inexistente (WARN)

 Errores (DefaultErrorHandler):
   MensajeInvalidoException -----------------------------> lead-calificado.clientes.DLT
   otra excepción -> reintentos 2s, 4s, 8s, 16s, 32s ----> lead-calificado.clientes.DLT
   (en ambos casos: WARN con partición/offset/clave, commit del offset y sigue con el próximo mensaje)
```

- **Idempotente:** Kafka entrega "al menos una vez" y el evento no trae `eventId`, así que la clave natural hace que
  releer el tópico (por un reinicio o un rebalanceo) no duplique nada.
- **Sin carreras con la anonimización:** el `FOR SHARE` sobre la fila del cliente ordena el registro y la baja. O el
  registro ve al cliente anonimizado y no inserta, o la baja encuentra la fila y la borra.
- **El historial es una proyección:** la fuente de verdad sigue en `calificacion`.

### 3.6 Eventos publicados

Los servicios de aplicación publican **eventos de dominio** dentro de la transacción. `ClienteEventosListener` los
traduce a **eventos de integración** y los manda a Kafka **después del commit** (`AFTER_COMMIT`). Si el broker no
responde, la operación ya confirmada no se revierte y la falla queda en el log con el `clienteId`.

---

## 4. Entradas y salidas

| Dirección | Canal | Contraparte | Qué | Protección |
| --- | --- | --- | --- | --- |
| Entrada | REST `POST /clientes` | `leads` | Alta deduplicada | `X-Api-Key` de `leads` |
| Entrada | REST `GET /clientes/{id}`, `GET /clientes` | front, otros servicios | Ficha y búsqueda | — |
| Entrada | REST `PATCH`, `PUT/DELETE no-contactar`, `POST anonimizacion` | front (personal) | Escrituras del personal | `If-Match` en `PATCH`; email de confirmación en la anonimización |
| Entrada | REST `GET /clientes/{id}/situacion-crediticia` | `calificacion` | Situación crediticia | `X-Api-Key` de `calificacion` |
| Entrada | REST `GET /clientes/{id}/historial` | front | Historial de calificaciones | — |
| Entrada | Kafka `lead-calificado` | `calificacion` | Cada cálculo o corrección de una calificación | Validación del mensaje y tópico de fallidos |
| Salida | Kafka `cliente-actualizado` | `leads` | Qué campos de la ficha cambiaron | Sin datos personales |
| Salida | Kafka `cliente-anonimizado` | `leads` | Qué cliente se anonimizó | Sin datos personales |
| Salida | Kafka `lead-calificado.clientes.DLT` | operación (manual) | Mensajes de `lead-calificado` que no se pudieron registrar | Interno |
| Salida (interna) | Puerto `SituacionCrediticiaPort` | fuente simulada (BCRA en la Segunda Parte) | CUIL descifrado, solo en memoria | Timeout y circuit breaker |

Headers transversales:

| Header | Dirección | Para qué |
| --- | --- | --- |
| `X-Correlation-Id` | entrada y salida | Lo toma del request (o genera un UUID), lo pone en todos los logs, lo devuelve y lo propaga a los eventos |
| `X-Usuario` | entrada, opcional | Quién operó (por ejemplo `tm-ana`); queda en los logs. Reemplaza a la autenticación en la Primera Parte |
| `X-Api-Key` | entrada | Clave de servicio de `leads` o de `calificacion`; cada una abre solo su endpoint |
| `ETag` / `If-Match` | salida / entrada | Versión de la ficha para la edición con control de concurrencia |

---

## 5. API REST

Contrato completo en Swagger UI: `http://localhost:8082/swagger-ui.html` (JSON en `/v3/api-docs`).
Estado del servicio: `GET /actuator/health`.

| Método | Ruta | Quién lo usa | Respuestas |
| --- | --- | --- | --- |
| `POST` | `/clientes` | Solo `leads` (con `X-Api-Key`) | `201` nuevo · `200` ya existía (por CUIL o por email) · `401` · `422` |
| `GET` | `/clientes/{id}` | Front, otros servicios | `200` con `ETag` · `404` · `410` anonimizado |
| `GET` | `/clientes?telefono=&email=&q=&provincia=&page=&size=` | Front, otros servicios | `200` · `422` |
| `PATCH` | `/clientes/{id}` | Front (personal del negocio), con `If-Match` | `200` con `ETag` · `404` · `409` · `410` · `412` · `422` · `428` |
| `PUT` | `/clientes/{id}/no-contactar` | Front (personal del negocio) | `204` · `404` · `410` |
| `DELETE` | `/clientes/{id}/no-contactar` | Front (personal del negocio) | `204` · `404` · `410` |
| `POST` | `/clientes/{id}/anonimizacion` | Front (personal del negocio), con el email como confirmación | `204` · `404` · `422` |
| `GET` | `/clientes/{id}/situacion-crediticia` | Solo `calificacion` (con su `X-Api-Key`) | `200` con `estado` · `401` · `404` · `410` |
| `GET` | `/clientes/{id}/historial` | Front (ficha del lead) | `200` (lista vacía si no tiene) · `404` · `410` |

Cuando entre el gateway (próxima iteración), las rutas se van a exponer como `/api/clientes/**`. Mientras tanto se
llama directo al puerto 8082.

Las escrituras del personal no piden `X-Api-Key`, porque no hay autenticación de usuarios en la Primera Parte. Aceptan
el header opcional `X-Usuario`, que queda en los logs junto al `correlationId`.

### 5.1 Alta de cliente (para `leads`)

```http
POST /clientes
X-Api-Key: <CLIENTES_CLAVE_LEADS>
Content-Type: application/json

{
  "nombre": "Martina",            // obligatorio, 2 a 120: letras, espacios, apóstrofo o guion
  "apellido": "López",            // obligatorio, mismas reglas
  "email": "martina@gmail.com",   // obligatorio: es la clave de deduplicación
  "telefono": "11 1234-5678",     // opcional; se normaliza a móvil argentino E.164
  "provincia": "BUENOS_AIRES",    // obligatoria, enum Provincia (sale de la ubicación del preformulario)
  "localidad": "San Isidro",      // opcional
  "canalOrigen": "INSTAGRAM",     // opcional, enum CanalOrigen
  "cuil": "20-12345678-6",        // solo si el prospecto pidió financiación; con o sin guiones
  "consentimiento": true          // obligatorio junto con el CUIL (precalificación crediticia)
}
```

```http
HTTP/1.1 201 Created              (o 200 OK si el CUIL o el email ya pertenecían a un cliente activo)
Location: /clientes/a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d

{ "clienteId": "a1b2c3d4-...", "nuevo": true, "coincidencia": "NINGUNA" }
```

- **201 y 200 se tratan igual:** en los dos casos `clienteId` es el cliente que tiene que referenciar el lead.
  `coincidencia` vale `NINGUNA`, `EMAIL` o `CUIL`.
- **`canalOrigen`:** `INSTAGRAM`, `TIKTOK`, `MARKETPLACE`, `FACEBOOK`, `META_ADS`, `WHATSAPP`, `REFERIDO` u `OTRO`.
- **Teléfono:** se normaliza con libphonenumber a móvil argentino (`+549...`). `11 1234-5678`, `011 15 1234-5678` y
  `+54 9 11 1234-5678` son el mismo número.
- **CUIL y consentimiento:** van juntos, y solo cuando el preformulario pide financiación. `clientes` no conoce
  `solicitaFinanciacion`: la regla "con financiación, el CUIL es obligatorio" la valida `leads`.
- **Errores a propagar al front:** `422` con `codigo` `VALIDACION` (lista `errores`), `TELEFONO_INVALIDO`
  (campo `telefono`), `CUIL_INVALIDO` (campo `cuil`) o `CONSENTIMIENTO_REQUERIDO` (campo `consentimiento`).
  Un `401` (`CLAVE_SERVICIO_INVALIDA`) indica una clave mal configurada en `leads`.
- **El CUIL nunca vuelve completo:** se guarda cifrado y la ficha lo muestra enmascarado (`XX-XXXX5678-X`). No hay
  búsqueda por CUIL.

### 5.2 Ficha y búsqueda

`GET /clientes/{id}` devuelve la ficha con el header `ETag`:

| Campo | Notas |
| --- | --- |
| `id`, `nombre`, `apellido`, `email`, `telefono`, `telefonoAlternativo`, `provincia`, `localidad` | Datos de la persona |
| `canalOrigen`, `ultimoCanal`, `contactosRegistrados`, `ultimoContactoEn` | Por qué canal llegó la primera vez y cuántas veces consultó (un cliente recurrente es una señal de interés) |
| `noContactar`, `noContactarDesde` | Marca "no contactar" |
| `cuil` | Enmascarado o `null` |
| `consentimientosVigentes` | Lista de `{ finalidad, versionTexto, otorgadoEn }` |
| `requiereRevision`, `motivoRevision` | Marca de posible duplicado: `CUIL_DISTINTO` o `POSIBLE_DUPLICADO` |

`GET /clientes` busca entre los clientes activos combinando filtros con AND: `telefono` y `email` (coincidencia exacta
después de normalizar), `q` (nombre y apellido sin distinguir mayúsculas, acentos ni ñ/n) y `provincia`. Ordena por
último contacto, el más reciente primero. Página de 20 por defecto y 50 como máximo; un `size` mayor se limita sin
avisar. La respuesta es `{ contenido, pagina, tamanio, totalElementos, totalPaginas }`, y cada elemento es un resumen
sin CUIL ni consentimientos.

### 5.3 Edición, no contactar y anonimización (para el front)

**Edición con control de concurrencia.** El front guarda el `ETag` de `GET /clientes/{id}` y lo manda en `If-Match`.
Si otro usuario (o un recontacto) cambió la ficha mientras tanto, la respuesta es `412` y hay que recargarla.

```http
PATCH /clientes/{id}
If-Match: W/"3"
X-Usuario: tm-ana
Content-Type: application/json

{ "apellido": "Gómez", "telefono": "0351 15 412-3456" }
```

- **Campos editables:** `nombre`, `apellido`, `email`, `telefono`, `telefonoAlternativo`, `provincia` y `localidad`,
  con las mismas reglas que el alta.
  - Un campo ausente, `null` o en blanco **no se modifica**: no se puede vaciar el teléfono, el alternativo ni la
    localidad.
  - El alternativo no puede ser igual al principal (`422`, campo `telefonoAlternativo`).
- **Resolver una revisión:** `{ "requiereRevision": false }` quita la marca y su `motivoRevision`. Enviar `true`
  responde `422`, porque la marca la pone solo el alta.
- **Respuesta:** `200` devuelve la ficha y el `ETag` nuevo. Si no hubo cambios reales, el `ETag` no cambia.
- **Errores:**
  - `409 EMAIL_EN_USO`: el email pertenece a otro cliente;
  - `412 VERSION_DESACTUALIZADA`: hay que recargar la ficha;
  - `428 PRECONDICION_REQUERIDA`: falta `If-Match`.

**No contactar.** Cuando el cliente pide que no lo llamen más: `PUT /clientes/{id}/no-contactar`. Para deshacerlo,
`DELETE` sobre la misma ruta. No piden `If-Match`, son idempotentes y no borran datos. Si la persona vuelve a llenar el
preformulario, el alta quita la marca.

**Anonimización (derecho de supresión).** Es irreversible. Exige el email del cliente como confirmación, en el cuerpo
y nunca en la URL:

```http
POST /clientes/{id}/anonimizacion
Content-Type: application/json

{ "email": "martina@gmail.com" }
```

`204`, también si se repite. `422 CONFIRMACION_INVALIDA` si el email no es el del cliente.

**CORS (cuando entre el gateway).** Si el front llama desde otro origen, el gateway tiene que exponer `ETag`
(`Access-Control-Expose-Headers`) y permitir `If-Match` y `X-Usuario` (`Access-Control-Allow-Headers`). Sin eso, el
navegador no deja leer la versión y la edición no funciona.

### 5.4 Situación crediticia (para `calificacion`)

```http
GET /clientes/{id}/situacion-crediticia
X-Api-Key: <CLIENTES_CLAVE_CALIFICACION>
```

```json
{ "clienteId": "a1b2...", "estado": "CON_DATOS", "situacion": 1, "periodo": "202609",
  "fuente": "SIMULADA", "consultadaEn": "2026-10-10T13:02:45.123456Z" }
```

| `estado` | Significa | Puntos sugeridos en `calificacion` |
| --- | --- | --- |
| `CON_DATOS` | La fuente informó una `situacion` de 1 a 6 | 1–2 → +20 · 3–6 → −25 |
| `SIN_REGISTROS` | La fuente no tiene registros de ese CUIL | 0 |
| `SIN_CUIL` | El cliente no pidió financiación; no se consulta la fuente | 0 |
| `NO_DISPONIBLE` | La fuente falló o no respondió en 2 s; reintentar más tarde | 0 |
| `SIN_CONSENTIMIENTO` | Reservado: hoy no se produce (aparece con la revocación de consentimientos) | 0 |

- Los campos que no aplican van en `null`. La respuesta nunca incluye el CUIL.
- El cliente HTTP de `calificacion` tiene que esperar **más de 2 s**: si corta antes, recibe un error en lugar del
  estado.
- **Fuente simulada (Primera Parte):** el último dígito del DNI decide el resultado: 0–4 → situación 1, 5–6 → 2,
  7 → 3, 8 → 5, 9 → sin registros. Por ejemplo, `20-12345678-6` da situación 5.

### 5.5 Historial de calificaciones (para el front)

```http
GET /clientes/{id}/historial
```

```json
{ "clienteId": "3fa8...", "calificaciones": [
  { "leadId": "550e...", "nivel": "POTABLE", "puntaje": 35, "origen": "MANUAL",
    "calculadaEn": "2026-10-11T19:05:00Z", "observacion": "Anticipo confirmado", "motivos": [] },
  { "leadId": "550e...", "nivel": "MUY_POTABLE", "puntaje": 60, "origen": "AUTOMATICA",
    "calculadaEn": "2026-10-11T18:30:00Z", "observacion": "Calificación automática calculada por motor de reglas",
    "motivos": ["[REGLA_COBERTURA]: Cobertura aportada: 60.00% ...", "..."] } ] }
```

- Devuelve todas las calificaciones de todos los leads del cliente, la calculada más recientemente primero y sin
  paginar.
- `nivel` (`MUY_POTABLE`, `POTABLE`, `NO_POTABLE`) y `origen` (`AUTOMATICA`, `MANUAL`) son texto: es el vocabulario
  de `calificacion`, y un valor nuevo no rompe nada.
- `calculadaEn` está en UTC. `observacion` puede ser `null`.
- Se alimenta de un evento, así que una calificación nueva puede tardar unos segundos en aparecer.

---

## 6. Eventos (Kafka)

### 6.1 Que publica `clientes` (para `leads`)

| Tópico | Evento | Cuándo | Payload |
| --- | --- | --- | --- |
| `cliente-actualizado` | `ClienteActualizado` | Una edición o la marca no contactar cambian algo | `{ clienteId, campos: [NOMBRE, APELLIDO, EMAIL, TELEFONO, PROVINCIA, LOCALIDAD, NO_CONTACTAR] }` |
| `cliente-anonimizado` | `ClienteAnonimizado` | Se anonimiza un cliente | `{ clienteId }` |

```json
{
  "eventId": "5d1e7c3a-2b4f-4a6e-9c8d-7e6f5a4b3c2d",
  "eventType": "ClienteActualizado",
  "version": 1,
  "occurredAt": "2026-10-08T13:10:02Z",
  "correlationId": "c0a8012e-7f3b-4d8a-b1c2-9d8e7f6a5b4c",
  "payload": { "clienteId": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d", "campos": ["NO_CONTACTAR"] }
}
```

- **Clave del mensaje = `clienteId`:** los eventos de un mismo cliente llegan en orden.
- **JSON sin headers de tipo:** no hace falta compartir clases Java. Los records están en `contracts`
  (`com.uade.crm.contracts.clientes`) para quien quiera usarlos.
- **Sin datos personales** (patrón *event notification*): para ver el valor nuevo, `GET /clientes/{id}`.
- No publican evento:
  - un recontacto, aunque quite la marca no contactar;
  - un cambio del teléfono alternativo, de la marca de revisión, del CUIL o de los consentimientos.
- **Se publican después del commit.** No hay outbox en la Primera Parte: si el broker falla, la falla queda en el log y
  `leads` puede resincronizar con `GET /clientes/{id}`.

### 6.2 Que consume `clientes` (de `calificacion`)

| Tópico | Grupo | Clave | Para qué |
| --- | --- | --- | --- |
| `lead-calificado` | `clientes-historial` | `leadId` | Registrar cada calificación en el historial del cliente |

```json
{ "leadId": "550e8400-e29b-41d4-a716-446655440000", "clienteId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "puntos": 60, "nivel": "MUY_POTABLE", "origen": "AUTOMATICA", "fecha": "2026-10-11T15:30:00",
  "observacion": "Calificación automática calculada por motor de reglas", "motivos": ["..."] }
```

- **Formato:** obligatorios `leadId`, `clienteId`, `puntos`, `nivel`, `origen` y `fecha`; opcionales `observacion` y
  `motivos`. Los campos desconocidos se ignoran. También acepta el evento envuelto en el sobre común, dentro de
  `payload` y con `eventType` = `LeadCalificado`.
- **`fecha`:** si no indica zona, se interpreta en `clientes.historial.zona-fecha` (Buenos Aires por defecto); si trae
  offset o `Z`, se respeta.
- **Primera lectura:** desde el principio del tópico (`auto-offset-reset: earliest`), así que carga lo que
  `calificacion` ya había publicado.
- **Tópico propio:** `clientes` no crea `lead-calificado`, porque es de `calificacion`.
- **Mensajes que no se pueden registrar:** van a `lead-calificado.clientes.DLT` con su contenido original, como se
  describe en [3.5](#35-historial-de-calificaciones-consume-lead-calificado). El reproceso es manual: republicar el
  mensaje en `lead-calificado` (es seguro por la idempotencia).

---

## 7. Errores

Todos los errores usan **ProblemDetail** (RFC 7807, `application/problem+json`) con un `codigo` estable y el
`correlationId`. Los de validación suman una lista `errores` de `{ campo, mensaje }`, que permite marcar cada campo en
el formulario.

```json
{ "type": "https://crm.uade.local/errores/validacion", "title": "Datos inválidos", "status": 422,
  "detail": "Hay campos que no cumplen las validaciones", "codigo": "VALIDACION", "correlationId": "c0a8...",
  "errores": [ { "campo": "email", "mensaje": "debe ser un email válido con dominio" } ] }
```

| `codigo` | HTTP | Cuándo |
| --- | --- | --- |
| `VALIDACION` | 422 | Datos inválidos (con la lista `errores`) |
| `JSON_INVALIDO` | 400 | Cuerpo mal formado |
| `TELEFONO_INVALIDO` | 422 | Teléfono que no se puede normalizar a móvil argentino |
| `CUIL_INVALIDO` | 422 | CUIL con formato o dígito verificador inválido |
| `CONSENTIMIENTO_REQUERIDO` | 422 | CUIL sin consentimiento de precalificación |
| `CLAVE_SERVICIO_INVALIDA` | 401 | Falta `X-Api-Key` o no es la del servicio autorizado para ese endpoint |
| `CLIENTE_NO_ENCONTRADO` | 404 | El ID nunca existió |
| `CLIENTE_ANONIMIZADO` | 410 | El cliente fue anonimizado |
| `EMAIL_EN_USO` | 409 | Al editar, el email pertenece a otro cliente activo |
| `PRECONDICION_REQUERIDA` | 428 | `PATCH` sin `If-Match` |
| `VERSION_DESACTUALIZADA` | 412 | `If-Match` no coincide con la versión actual |
| `CONFIRMACION_INVALIDA` | 422 | El email de confirmación de la anonimización no es el del cliente |
| `SOLICITUD_INVALIDA` | 400 | Otros errores de la solicitud que resuelve Spring (por ejemplo, un UUID mal formado en la ruta) |
| `ERROR_INTERNO` | 500 | Error inesperado (nunca por culpa de la fuente de situación crediticia) |

---

## 8. Modelo de datos

Una migración de Flyway por change; nunca se edita una migración ya aplicada.

| Migración | Tabla | Qué guarda |
| --- | --- | --- |
| `V1__cliente.sql` | `cliente` | La ficha: datos personales, canales, contador de contactos, marca de revisión, estado (`ACTIVO` / `ANONIMIZADO`) y `version` para el control optimista |
| `V2__no_contactar.sql` | `cliente` | `no_contactar` y `no_contactar_desde`; un check que garantiza que un anonimizado no conserva datos |
| `V3__cuil_y_consentimiento.sql` | `cliente`, `consentimiento` | `cuil_cifrado` (AES-GCM), `cuil_hash` (índice ciego) y `cuil_ultimos4`; consentimientos por finalidad con `version_texto`, `otorgado_en` y `revocado_en` |
| `V4__situacion_crediticia.sql` | `situacion_crediticia` | Caché por cliente: estado, situación, período, fuente, `consultada_en` y `vigente_hasta` |
| `V5__historial_calificacion.sql` | `historial_calificacion` | Calificaciones: `lead_id`, `nivel`, `puntaje`, `origen`, `calculada_en`, `observacion` y `motivos` (JSONB) |

```
 cliente (1) ----< consentimiento (N)
    |   \
    |    '---- situacion_crediticia (0..1)
    |
    '--------< historial_calificacion (N)     lead_id -> referencia a leads, sin FK
```

**Restricciones e índices que cuidan los datos aunque el código se equivoque:**

- **Unicidad entre clientes activos:**
  - índices únicos parciales sobre `email`, `cuil_hash` y (`cliente_id`, `finalidad`) entre consentimientos vigentes;
  - después de una anonimización, el email y el CUIL quedan libres.
- **Checks de coherencia:**
  - un cliente activo siempre tiene email;
  - un anonimizado no conserva datos;
  - las columnas del CUIL van todas o ninguna;
  - la caché solo guarda `CON_DATOS` o `SIN_REGISTROS`;
  - `motivos` es un array JSON.
- **Clave única natural del historial:** (`lead_id`, `origen`, `calculada_en`).
- **Índices parciales sobre activos** para teléfono, provincia y último contacto, e índice
  (`cliente_id`, `calculada_en DESC`) para el historial.

---

## 9. Seguridad y protección de datos

`clientes` es el **único servicio que conoce el CUIL** (Ley 25.326, principio de minimización).

| Medida | Cómo |
| --- | --- |
| CUIL cifrado | AES-256-GCM con un IV aleatorio por valor (`CLIENTES_CUIL_CLAVE`). Se descifra solo en memoria y solo para consultar la fuente de situación crediticia |
| Deduplicación sin descifrar | Índice ciego HMAC-SHA256 (`CLIENTES_CUIL_HMAC`) con índice único entre activos |
| CUIL enmascarado | La ficha muestra `XX-XXXX5678-X`. Nunca viaja en URLs, eventos ni logs |
| Logs sin datos personales | Un converter de logback (`%mask`) enmascara CUIL y teléfonos aunque alguien los loguee por error. El consumidor nunca loguea `motivos` ni `observacion` |
| Eventos sin datos personales | Solo `clienteId` y nombres de campos |
| Claves de servicio | `X-Api-Key` validada dentro de `clientes` (no solo en el gateway), una clave por consumidor, comparación en tiempo constante. Al arrancar se exige un mínimo de 32 caracteres, que las claves sean distintas y que no haya placeholders sin resolver. Nunca se loguean |
| Consentimiento informado | Por finalidad, con versión del texto aceptado. Un cliente con CUIL siempre tiene vigente el de precalificación |
| Derecho de supresión | Anonimización que borra datos personales, CUIL, situación crediticia e historial, y revoca consentimientos, en una transacción |
| Confirmación de baja | El email de confirmación va en el cuerpo, nunca en la URL |

---

## 10. Patrones de diseño

| Patrón | Dónde | Qué resuelve |
| --- | --- | --- |
| **Arquitectura hexagonal (puertos y adaptadores)** | `domain/port` + `infrastructure` | El dominio no conoce JPA, Kafka ni HTTP. Cambiar la base, el broker o la fuente crediticia toca solo un adaptador |
| **Repository** | `ClienteRepositorio`, `HistorialCalificaciones`, `CacheSituacionCrediticia` | Aísla al dominio de la persistencia; las sentencias concurrentes delicadas (`FOR SHARE`, `ON CONFLICT`) quedan en el adaptador |
| **Factory** | `ClienteFactory` | Crea un cliente válido desde el alta: normaliza teléfono y email, valida y cifra el CUIL, fija canales y fechas |
| **Value Object** | `Email`, `Telefono`, `Cuil`, `CuilProtegido`, `NombreBusqueda` | La validación, normalización y enmascarado viven en un solo lugar; un valor inválido no puede existir |
| **Aggregate** | `Cliente` (con sus `Consentimiento`) | Protege invariantes, como CUIL ⇔ consentimiento de precalificación vigente o que un anonimizado no tiene datos |
| **Observer (eventos de dominio)** | `ClienteActualizadoEvento` / `ClienteAnonimizadoEvento` + listeners `AFTER_COMMIT` y `BEFORE_COMMIT` | El servicio de anonimización no conoce Kafka, la caché ni el historial: cada módulo reacciona al evento |
| **Event Notification** | `cliente-actualizado`, `cliente-anonimizado` | Avisar el cambio sin mandar datos personales; el interesado pide la ficha por REST |
| **Strategy + Adapter** | `SituacionCrediticiaPort` con `SimuladaSituacionAdapter` (y el SOAP del BCRA en la Segunda Parte), elegido por `clientes.situacion-crediticia.modo` | Cambiar la fuente sin tocar el dominio, el servicio ni la API |
| **Decorator** | `SituacionCrediticiaConCache` → `SituacionCrediticiaResiliente` → fuente | Caché y resiliencia agregadas al mismo puerto sin modificar el adaptador; no cambian cuando llegue la fuente real |
| **Circuit Breaker** | Resilience4j en `SituacionCrediticiaResiliente` | Una fuente caída no degrada al servicio: después de varias fallas responde `NO_DISPONIBLE` al instante |
| **Translator / capa anticorrupción** | `LeadCalificadoTraductor` | El formato de `calificacion` (JSON plano, fecha sin zona, `puntos`) no entra al dominio; adentro todo es `CalificacionHistorica` |
| **Idempotent Consumer** | Clave natural + `ON CONFLICT DO NOTHING` | Releer o recibir dos veces un mensaje no duplica el historial |
| **Dead Letter Channel + Retry con backoff** | `ConsumidorKafkaConfig` (`DefaultErrorHandler` + `DeadLetterPublishingRecoverer`) | Un mensaje malo no traba la partición ni se pierde |
| **Optimistic Locking** | `version` + `ETag` / `If-Match` | Dos personas editando la misma ficha no se pisan |
| **Interceptor** | `ClaveServicioInterceptor`, `IfMatchInterceptor`, `CorrelationIdFilter`, `UsuarioFilter` | Reglas transversales declarativas (`@RequiereClaveServicio`, `@RequiereIfMatch`) fuera de los controllers |
| **Servicio de aplicación por caso de uso** | `AltaClienteService`, `EdicionClienteService`, ... | Cada caso de uso es chico y testeable; no hace falta una fachada que solo delegue |

---

## 11. Configuración

### 11.1 Variables de entorno

| Variable | Obligatoria | Para qué |
| --- | --- | --- |
| `CLIENTES_CUIL_CLAVE`, `CLIENTES_CUIL_HMAC` | Sí | Cifrado e índice ciego del CUIL (32 bytes en base64). **No se pueden cambiar** una vez que hay datos: con otra `CLIENTES_CUIL_CLAVE` los CUIL guardados quedan ilegibles y con otra `CLIENTES_CUIL_HMAC` la deduplicación por CUIL deja de encontrarlos. La rotación no está implementada |
| `CLIENTES_CLAVE_LEADS` | Sí | Clave que `leads` manda en `X-Api-Key` para dar de alta. Mínimo 32 caracteres; la misma se configura en `leads`. Ejemplo: `openssl rand -base64 32` |
| `CLIENTES_CLAVE_CALIFICACION` | Sí | Clave que `calificacion` manda en `X-Api-Key` para la situación crediticia. Mínimo 32 caracteres y distinta de la de `leads` |
| `CLIENTES_DB_USUARIO`, `CLIENTES_DB_CLAVE` | Sí (en docker) | Usuario y clave de `clientes_db`. `CLIENTES_DB_URL` es opcional (por defecto, la base del compose) |
| `KAFKA_BOOTSTRAP_SERVERS` | No | Broker de Kafka (por defecto `kafka:9092`, el del compose) |
| `CLIENTES_HISTORIAL_ZONA_FECHA` | No | Zona para la `fecha` de `LeadCalificado` sin zona. Por defecto `America/Argentina/Buenos_Aires` |

Sin las variables obligatorias, el servicio **no arranca, a propósito**: así no queda funcionando con una clave vacía o
conocida.

### 11.2 Propiedades (`application.yml`, prefijo `clientes`)

| Propiedad | Valor | Para qué |
| --- | --- | --- |
| `telefono.region-por-defecto` | `AR` | Región para normalizar teléfonos |
| `consentimiento.version-texto` | `v1` | Versión del texto legal que acepta el titular |
| `situacion-crediticia.modo` | `simulado` | `simulado` o `bcra` (Segunda Parte; hoy falla al arrancar) |
| `situacion-crediticia.vigencia` / `.timeout` | `30d` / `2s` | Caché y tiempo máximo de la fuente |
| `situacion-crediticia.simulado.demora` / `.forzar-no-disponible` | `0ms` / `false` | Para simular una fuente lenta o caída |
| `paginacion.tamanio-por-defecto` / `.tamanio-maximo` | `20` / `50` | Búsqueda |
| `kafka.topicos.*` | `cliente-actualizado`, `cliente-anonimizado`, `lead-calificado`, `lead-calificado.clientes.DLT` | Nombres de los tópicos |
| `kafka.particiones` / `.replicas` | `3` / `1` | Al crear los tópicos propios |
| `historial.zona-fecha` | Buenos Aires | Ver variable `CLIENTES_HISTORIAL_ZONA_FECHA` |
| `historial.reintentos.intentos` / `.espera-inicial` / `.multiplicador` | `5` / `2s` / `2` | Reintentos del consumidor antes del DLT (unos 62 s) |
| `resilience4j.circuitbreaker.instances.situacionCrediticia.*` | ventana 10, 50 % de fallas, 30 s abierto | Circuit breaker de la fuente |

**Perfiles:**
- `local` (por defecto): claves ficticias, y PostgreSQL y Kafka de `infra/docker-compose.yml` en el host.
- `docker`: lo usa el contenedor.
- `test`: valores cortos para que los tests de degradación y reintentos sean rápidos.

---

## 12. Cómo levantarlo y probarlo

### 12.1 Con Docker Compose (PostgreSQL + Kafka + servicio)

```bash
cp infra/.env.example infra/.env      # completar las claves (ver comentarios del archivo)
docker compose -f infra/docker-compose.yml up --build
```

El servicio queda en `http://localhost:8082`: Swagger UI en `/swagger-ui.html` y estado en `/actuator/health`.

Para desarrollo sin el contenedor del servicio: `mvn -pl clientes-service -am spring-boot:run` (perfil `local`), con
PostgreSQL y Kafka de `infra/docker-compose.yml`.

### 12.2 Probar los endpoints

Desde **Swagger UI** se pueden probar todos. Para el alta y la situación crediticia, completar `X-Api-Key` con la
clave correspondiente de `infra/.env`.

Con curl:

```bash
# Alta (como leads)
curl -i -X POST http://localhost:8082/clientes -H "X-Api-Key: $CLIENTES_CLAVE_LEADS" \
     -H "Content-Type: application/json" --data-binary @alta.json

# Ficha, búsqueda, situación crediticia (como calificacion) e historial
curl -i http://localhost:8082/clientes/{id}
curl -i "http://localhost:8082/clientes?q=martina&provincia=BUENOS_AIRES"
curl -i http://localhost:8082/clientes/{id}/situacion-crediticia -H "X-Api-Key: $CLIENTES_CLAVE_CALIFICACION"
curl -i http://localhost:8082/clientes/{id}/historial
```

> **curl en Windows:** pasar el JSON desde un archivo (`--data-binary @alta.json`). Pasado como argumento
> (`-d '{"apellido":"López"}'`), `curl.exe` puede convertir los acentos a otra codificación y el servicio responde
> `400 JSON_INVALIDO`.

### 12.3 Probar el historial sin `calificacion`

`clientes` no crea `lead-calificado`, así que primero hay que crearlo. Después se publica un evento a mano con la clave
`leadId` y el `clienteId` de un cliente dado de alta:

```bash
docker exec crm-clientes-kafka-1 /opt/kafka/bin/kafka-topics.sh --create --if-not-exists \
       --topic lead-calificado --partitions 3 --bootstrap-server localhost:9092

echo '550e8400-e29b-41d4-a716-446655440000|{"leadId":"550e8400-e29b-41d4-a716-446655440000","clienteId":"<clienteId>","puntos":60,"nivel":"MUY_POTABLE","origen":"AUTOMATICA","fecha":"2026-10-11T15:30:00","motivos":["[REGLA_COBERTURA]: +60"]}' \
  | docker exec -i crm-clientes-kafka-1 /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 \
       --topic lead-calificado --property parse.key=true --property key.separator='|'

curl http://localhost:8082/clientes/<clienteId>/historial

# Mensajes que fueron al tópico de fallidos
docker exec crm-clientes-kafka-1 /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
       --topic lead-calificado.clientes.DLT --from-beginning --property print.key=true --timeout-ms 5000
```

> En Git Bash (Windows), anteponer `MSYS_NO_PATHCONV=1` a los `docker exec`. Si no, Git Bash convierte
> `/opt/kafka/...` en una ruta de Windows.

---

## 13. Tests

```bash
mvn verify      # unitarios + integración (Testcontainers: necesita Docker)
```

- **Unitarios sin Spring:** dominio (invariantes del agregado, value objects y factory), traductor del evento, servicios
  con dobles de los puertos, decorators de caché y resiliencia, y backoff del consumidor.
- **Test de contrato del puerto:** `ContratoFuenteSituacionTest` verifica el timeout de la fuente. Lo tiene que
  extender también el futuro adaptador SOAP.
- **Integración con PostgreSQL y Kafka reales (Testcontainers):** endpoints con MockMvc, repositorios, publicación y
  consumo de eventos, tópico de fallidos, reintentos (con un trigger de Postgres que falla N veces), concurrencia
  (altas simultáneas, anonimización contra registro del historial) y ausencia de datos personales en los logs.
- Las clases de integración comparten un único contexto de Spring (`@PruebaIntegracion`), por eso cada una limpia sus
  datos.

---

## 14. Limitaciones conocidas y próximos pasos

- **Gateway:** entra en la próxima iteración (rutas `/api/clientes/**` y CORS). Hoy se llama directo al 8082.
- **Integración con los otros servicios:**
  - el flujo completo necesita que `leads` haga el alta con `X-Api-Key`, acepte `200` y envíe `provincia`;
  - y que los servicios compartan el mismo broker de Kafka: el compose de este repo levanta uno propio.
- **Sin outbox:** si Kafka falla justo después del commit, el evento se pierde (queda en el log). Está en el backlog
  (`clientes-10`).
- **Sin autenticación de usuarios:** `X-Usuario` es informativo.
- **Backlog** (detalle en §8 del documento semilla):
  - adaptador SOAP de precalificación (`clientes-09`);
  - outbox (`clientes-10`);
  - auditoría de accesos al CUIL (`clientes-11`);
  - fusión de duplicados (`clientes-12`);
  - privacidad ampliada: `PUT /cuil`, búsqueda por CUIL y revocación de consentimientos (`clientes-15`);
  - reactivación por stock (`clientes-16`);
  - notas (`clientes-06`) y preferencias (`clientes-07`).

---

## 15. Documentación relacionada

- **Especificaciones vigentes (OpenSpec):** `openspec/specs/`
  - `clientes-transversal`: errores, `correlationId`, logs, OpenAPI y claves de servicio;
  - `clientes-gestion`: alta, ficha, búsqueda, edición, no contactar y anonimización;
  - `clientes-eventos`: eventos que publica el servicio;
  - `clientes-privacidad`: CUIL y consentimientos;
  - `clientes-situacion-crediticia`;
  - `clientes-historial-calificaciones`.
- **Historia de cada cambio** (proposal, design, tasks): `openspec/changes/archive/`.
- **Documento semilla** con la visión, los ajustes a la arquitectura y el backlog: `docs/specs-seed/clientes-service-spec.md`.
- **Textos para el contrato del equipo** (`contratos-backend.md`): `docs/contrato-equipo/clientes-0X-para-contratos-backend.md`.
