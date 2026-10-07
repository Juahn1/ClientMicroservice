# ClientMicroservice

Servicio `clientes` del CRM de calificación de leads: ficha única del comprador, con alta deduplicada por email,
consulta y búsqueda. Diseño y decisiones en `openspec/` y en `docs/specs-seed/clientes-service-spec.md`.

## Módulos

| Módulo | Qué contiene |
| --- | --- |
| `contracts` | Tipos compartidos entre servicios, sin Spring: `Provincia` (24 jurisdicciones) y `CanalOrigen`. |
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
| `GET` | `/clientes/{id}` | Front, otros servicios | `200` con `ETag` · `404` |
| `GET` | `/clientes?telefono=&email=&q=&provincia=&page=&size=` | Front, otros servicios | `200` · `422` |

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
