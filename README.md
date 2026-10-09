# ClientMicroservice

Servicio `clientes` del CRM de calificación de leads: ficha única del comprador, con alta deduplicada por email,
consulta, búsqueda, edición, marca "no contactar" y anonimización (Ley 25.326). Publica eventos en Kafka. Diseño y decisiones en `openspec/` y en `docs/specs-seed/clientes-service-spec.md`.

## Módulos

| Módulo | Qué contiene |
| --- | --- |
| `contracts` | Tipos compartidos entre servicios, sin Spring: `Provincia` (24 jurisdicciones), `CanalOrigen`, el sobre `EventoIntegracion` y los eventos de `clientes`. |
| `clientes-service` | Spring Boot 3.5 / Java 21, puerto interno 8082, base `clientes_db` (PostgreSQL 16, Flyway). |

## Cómo levantarlo

```bash
cp infra/.env.example infra/.env      # completar las claves (ver comentarios del archivo)
docker compose -f infra/docker-compose.yml up --build
```

Variables obligatorias (sin ellas el servicio no arranca, a propósito):

| Variable | Para qué |
| --- | --- |
| `CLIENTES_CUIL_CLAVE`, `CLIENTES_CUIL_HMAC` | Cifrado e índice ciego del CUIL (32 bytes en base64). |
| `CLIENTES_CLAVE_LEADS` | Clave de servicio que `leads` manda en `X-Api-Key` para dar de alta clientes. Mínimo 32 caracteres; la misma clave se configura en `leads`. Ejemplo: `openssl rand -base64 32`. |

Para desarrollo sin Docker del servicio: `mvn -pl clientes-service -am spring-boot:run` con el perfil `local`
(usa PostgreSQL y Kafka de `infra/docker-compose.yml` y claves ficticias).

Tests: `mvn verify` (los de integración usan Testcontainers, así que necesitan Docker).

Contrato OpenAPI: `GET /v3/api-docs` y Swagger UI en `/swagger-ui.html`.

> **Probar con curl en Windows:** pasar el JSON desde un archivo (`--data-binary @alta.json`). Pasado como
> argumento (`-d '{"apellido":"López"}'`), `curl.exe` puede convertir los acentos a otra codificación y el
> servicio responde `400 JSON_INVALIDO`.

## API

A través del gateway las rutas se exponen como `/api/clientes/**`.

| Método | Ruta | Quién lo usa | Respuestas |
| --- | --- | --- | --- |
| `POST` | `/clientes` | Solo `leads` (con `X-Api-Key`) | `201` nuevo · `200` ya existía · `401` · `422` |
| `GET` | `/clientes/{id}` | Front, otros servicios | `200` con `ETag` · `404` · `410` anonimizado |
| `GET` | `/clientes?telefono=&email=&q=&provincia=&page=&size=` | Front, otros servicios | `200` · `422` |
| `PATCH` | `/clientes/{id}` | Front (personal del negocio), con `If-Match` | `200` con `ETag` · `404` · `409` · `410` · `412` · `422` · `428` |
| `PUT` | `/clientes/{id}/no-contactar` | Front (personal del negocio) | `204` · `404` · `410` |
| `DELETE` | `/clientes/{id}/no-contactar` | Front (personal del negocio) | `204` · `404` · `410` |
| `POST` | `/clientes/{id}/anonimizacion` | Front (personal del negocio), con el email como confirmación | `204` · `404` · `422` |

Las escrituras del personal no piden `X-Api-Key` (no hay autenticación de usuarios en la Primera Parte). Aceptan
el header opcional `X-Usuario` (por ejemplo `tm-ana`), que queda en los logs junto al `correlationId`.

Errores: ProblemDetail (`application/problem+json`) con `codigo`, `correlationId` y, en los de validación, una
lista `errores` de `{ campo, mensaje }` que permite marcar cada campo en el formulario.

### Contrato para `leads`: alta de cliente

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
  "canalOrigen": "INSTAGRAM"      // opcional, enum CanalOrigen
}
```

```http
HTTP/1.1 201 Created              (o 200 OK si el email ya pertenecía a un cliente activo)
Location: /clientes/a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d

{ "clienteId": "a1b2c3d4-...", "nuevo": true, "coincidencia": "NINGUNA" }
```

- **201 y 200 se tratan igual:** en los dos casos `clienteId` es el cliente a referenciar desde el lead.
- **Reintentos seguros:** reenviar la misma alta no crea otro cliente (suma un contacto al existente).
- **No se pisan datos:** si el email ya existía, no se cambian nombre, apellido ni provincia; solo se completan
  campos vacíos.
- **Errores a propagar al front:** `422` con `codigo` `VALIDACION` (lista `errores`) o `TELEFONO_INVALIDO`
  (campo `telefono`). Un `401` (`CLAVE_SERVICIO_INVALIDA`) indica una clave mal configurada en `leads`.

### Contrato para el front: edición, no contactar y anonimización

**Edición con control de concurrencia.** El front guarda el `ETag` de `GET /clientes/{id}` y lo manda en
`If-Match`. Si otro usuario (o un recontacto) cambió la ficha mientras tanto, la respuesta es `412` y hay que
recargarla.

```http
PATCH /clientes/{id}
If-Match: W/"3"
X-Usuario: tm-ana
Content-Type: application/json

{ "apellido": "Gómez", "telefono": "0351 15 412-3456" }
```

- Campos editables: `nombre`, `apellido`, `email`, `telefono`, `provincia`, `localidad`, con las mismas reglas que
  el alta. Un campo ausente, `null` o en blanco **no se modifica** (no se puede vaciar el teléfono ni la localidad).
- `200` devuelve la ficha y el `ETag` nuevo. Si no hubo cambios reales, el `ETag` no cambia.
- `409 EMAIL_EN_USO`: el email pertenece a otro cliente. `412 VERSION_DESACTUALIZADA`: recargar la ficha.
  `428 PRECONDICION_REQUERIDA`: falta `If-Match`.

**No contactar.** Cuando el cliente pide que no lo llamen más, `PUT /clientes/{id}/no-contactar`; para deshacerlo,
`DELETE` sobre la misma ruta. No piden `If-Match`, son idempotentes y no borran datos: la ficha muestra
`noContactar` y `noContactarDesde`, y la búsqueda incluye `noContactar`. Si la persona vuelve a llenar el
preformulario, el alta quita la marca.

**Anonimización (derecho de supresión).** Irreversible: borra nombre, apellido, email, teléfonos y localidad. Exige
el email del cliente como confirmación (va en el cuerpo, nunca en la URL):

```http
POST /clientes/{id}/anonimizacion
Content-Type: application/json

{ "email": "martina@gmail.com" }
```

- `204`, también si se repite. `422 CONFIRMACION_INVALIDA` si el email no es el del cliente.
- Después, `GET /clientes/{id}` responde `410 CLIENTE_ANONIMIZADO` y el email queda libre para un alta nueva.

**Gateway (CORS).** Si el front llama desde otro origen, el gateway tiene que exponer `ETag`
(`Access-Control-Expose-Headers`) y permitir `If-Match` y `X-Usuario` (`Access-Control-Allow-Headers`). Sin eso el
navegador no deja leer la versión y la edición no funciona.

### Eventos que publica `clientes` (para `leads`)

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

- La clave del mensaje es el `clienteId`: los eventos de un mismo cliente llegan en orden.
- JSON sin headers de tipo: no hace falta compartir clases Java. Los records están en `contracts`
  (`com.uade.crm.contracts.clientes`) para quien quiera usarlos.
- **Sin datos personales:** para ver el valor nuevo, `GET /clientes/{id}`. Con `NO_CONTACTAR`, la ficha dice si
  quedó marcado o desmarcado; `leads` saca o devuelve al cliente de la cola.
- Un recontacto que quita la marca **no** publica evento: el lead nuevo ya entra a la cola.
- Se publican después del commit. Si el broker no responde, la operación igual se confirma y la falla queda en el
  log con el `clienteId` (sin outbox en la Primera Parte); `leads` puede resincronizar con `GET /clientes/{id}`.
