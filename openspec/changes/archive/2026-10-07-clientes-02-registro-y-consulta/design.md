# Design: Registro y consulta de clientes

> Fuentes: `docs/specs-seed/clientes-service-spec.md` (§5) y el contrato del equipo `contratos-backend.md` (v1.1).
> Motivación y alcance: ver `proposal.md`. Requisitos: ver `specs/clientes-gestion/spec.md` y `specs/clientes-transversal/spec.md`.

## Context

- El change `clientes-01-base-servicio` está implementado y archivado. Ya existen `ApiExceptionHandler` (ProblemDetail con `codigo`, `errores` y `correlationId`), `CorrelationIdFilter`, el enmascarado de CUIL y teléfono en logs (`Enmascarador` + `%mask`), springdoc, Flyway sobre `clientes_db` y `ClientesProperties` con validación al arrancar.
- Arquitectura en capas `api → application → domain ← infrastructure`; el dominio no depende de Spring Data (seed §1).
- Paquete base `com.uade.crm.clientes`, puerto 8082. Por el gateway las rutas quedan en `/api/clientes/**`.
- El contrato del equipo cambia los supuestos del seed. El alta la llama `leads`, no el front. El email es obligatorio y el teléfono opcional. El nombre se separa en `nombre` y `apellido`. El preformulario no informa canal.
- Volumen de referencia: entre 150 y 200 contactos por día, unos 70.000 registros por año en el peor caso (§5.11).

## Goals / Non-Goals

**Goals:**
- Alta idempotente y segura frente a reintentos y concurrencia, con deduplicación por email.
- Alta de escritura restringida a servicios autorizados, sin depender del gateway.
- Ficha con `ETag` y búsqueda paginada con un tope fijo.

**Non-Goals:**
- CUIL, consentimientos y deduplicación por CUIL → change 04.
- `PATCH`, anonimización, 410 y eventos Kafka → change 03.
- Historial de calificaciones → change `clientes-08-historial-calificaciones`.
- Autenticación de usuarios: no la hay en la Primera Parte (S-03). La clave de servicio autentica servicios, no personas.

## Decisions

### D1. Estructura de paquetes

```text
contracts (módulo Maven nuevo)
└── com.uade.crm.contracts      Provincia, CanalOrigen

com.uade.crm.clientes
├── api
│   ├── ClienteController
│   ├── dto                     AltaClienteRequest, AltaClienteResponse, ClienteResponse,
│   │                           ClienteResumenResponse, PaginaResponse<T> (records)
│   └── seguridad               @RequiereClaveServicio + ClaveServicioInterceptor
├── application                 AltaClienteService, ConsultaClienteService
├── domain
│   ├── model                   Cliente, EstadoCliente, Coincidencia, ResultadoAlta
│   ├── valor                   Telefono, Email, NombreBusqueda (+ Enmascarador existente)
│   ├── port                    ClienteRepositorio, FiltroBusqueda
│   └── ClienteFactory
└── infrastructure/persistence  ClienteEntity, ClienteJpaRepository, adaptador del puerto
```

Un servicio de aplicación por caso de uso (S-04). El dominio define el puerto `ClienteRepositorio` y la infraestructura lo implementa con Spring Data, así el dominio se testea sin JPA.

### D2. Clave de servicio para el alta

- `POST /clientes` exige el header `X-Api-Key` y lo compara con `clientes.seguridad.clave-leads` (`${CLIENTES_CLAVE_LEADS}`).
- La validación la hace un `HandlerInterceptor` que actúa sobre los handlers anotados con `@RequiereClaveServicio`. Corre antes de leer y validar el cuerpo, así que un pedido sin clave recibe 401 y no 422.
- Se compara con `MessageDigest.isEqual` (tiempo constante) para no filtrar información por timing.
- Si la clave falta o no coincide, el interceptor lanza una excepción que traduce `ApiExceptionHandler`: 401 `CLAVE_SERVICIO_INVALIDA` en ProblemDetail, con un header `WWW-Authenticate` ad hoc.
- `ClientesProperties` exige una clave de al menos 32 caracteres, igual que las claves del CUIL. Así, una variable sin resolver hace fallar el arranque en lugar de dejar el endpoint abierto.
- La clave nunca se loguea: el servicio no registra headers y `toString()` de la propiedad la enmascara.
- Los `GET` no la piden, porque el front los usa a través del gateway.

**Por qué un interceptor y no un filtro de servlet:** reutiliza `ApiExceptionHandler` y el formato de errores sin duplicarlo, y la anotación deja explícito qué endpoints son internos. El change 04 lo reutiliza para `PUT /cuil`.
**Alternativas descartadas:**
- Filtrar solo en el gateway: no protege si alguien llega directo a `clientes:8082` por la red de Docker.
- JWT entre servicios o mTLS: necesitan un servidor de identidad o certificados. Quedan para el Integrador y se pueden incorporar sin tocar el dominio.

### D3. Modelo de datos: `V1__cliente.sql`

Parte de §5.2 con desvíos deliberados que salen del contrato del equipo:

```sql
CREATE TABLE cliente (
    id                     UUID PRIMARY KEY,
    nombre                 VARCHAR(120) NOT NULL,
    apellido               VARCHAR(120) NOT NULL,          -- contrato: nombre y apellido separados
    nombre_busqueda        VARCHAR(241) NOT NULL,          -- "nombre apellido" normalizado (D6)
    email                  VARCHAR(254),                   -- normalizado; null solo al anonimizar (03)
    telefono_e164          VARCHAR(20),                    -- opcional según el contrato
    telefono_alternativo   VARCHAR(20),
    provincia              VARCHAR(40)  NOT NULL,
    localidad              VARCHAR(120),
    canal_origen           VARCHAR(20),                    -- opcional según el contrato
    ultimo_canal           VARCHAR(20),
    contactos_registrados  INT          NOT NULL DEFAULT 1,
    ultimo_contacto_en     TIMESTAMPTZ  NOT NULL,
    requiere_revision      BOOLEAN      NOT NULL DEFAULT FALSE,
    motivo_revision        VARCHAR(40),
    estado                 VARCHAR(15)  NOT NULL DEFAULT 'ACTIVO',  -- ACTIVO | ANONIMIZADO
    version                BIGINT       NOT NULL DEFAULT 0,
    creado_en              TIMESTAMPTZ  NOT NULL,
    actualizado_en         TIMESTAMPTZ  NOT NULL,
    anonimizado_en         TIMESTAMPTZ,
    CONSTRAINT ck_cliente_activo_con_email CHECK (estado <> 'ACTIVO' OR email IS NOT NULL)
);
CREATE UNIQUE INDEX ux_cliente_email_activo     ON cliente (email)                   WHERE estado = 'ACTIVO';
CREATE INDEX        ix_cliente_telefono_activo  ON cliente (telefono_e164)           WHERE estado = 'ACTIVO';
CREATE INDEX        ix_cliente_provincia        ON cliente (provincia)               WHERE estado = 'ACTIVO';
CREATE INDEX        ix_cliente_ultimo_contacto  ON cliente (ultimo_contacto_en DESC) WHERE estado = 'ACTIVO';
```

| Diferencia con §5.2 | Por qué |
| --- | --- |
| Índice único sobre `email`, no sobre `telefono_e164` | La clave de deduplicación pasa a ser el email (D4). El teléfono es opcional y una familia lo puede compartir, así que su índice deja de ser único. |
| `apellido`, `nombre_busqueda` | Nombre y apellido separados (contrato) y búsqueda sin acentos (D6). |
| `canal_origen` y `ultimo_canal` sin `NOT NULL` | El preformulario todavía no informa el canal. |
| `ix_cliente_ultimo_contacto` | La búsqueda sin filtros ordena por esta columna; el índice evita ordenar la tabla entera. |

- Las columnas `cuil_*` llegan con V2 (change 04), como prevé el seed.
- Una migración por change (S-05); una migración ya mergeada no se edita.

### D4. Algoritmo de alta (deduplicación por email)

1. Validar el request (Bean Validation) y normalizar: email (`trim` + minúsculas), teléfono si viene (D5), nombre y apellido recortados.
2. Buscar un cliente `ACTIVO` con ese email.
3. Si no existe → `ClienteFactory.crear(...)`: `contactosRegistrados = 1`, `ultimoCanal = canalOrigen`, `nombreBusqueda` calculado y fechas. Responde **201** con `Location`.
4. Si existe → `Cliente.registrarRecontacto(...)`. Responde **200** con `Location`. El recontacto:
   - **no modifica** nombre, apellido ni provincia;
   - completa `localidad` y `canalOrigen` solo si estaban vacíos;
   - si llega un teléfono: lo guarda como principal si no había ninguno; si es distinto del principal, lo guarda en `telefono_alternativo` si ese campo estaba vacío;
   - suma 1 a `contactosRegistrados`, actualiza `ultimoContactoEn` y, si llega canal, `ultimoCanal`.
5. **Concurrencia:** si dos altas con el mismo email chocan contra `ux_cliente_email_activo`, la que pierde recibe `DataIntegrityViolationException`. `AltaClienteService` no es transaccional: ejecuta cada intento en una transacción propia (`TransactionTemplate`) y, ante esa excepción, reintenta **una vez**. En el reintento encuentra al cliente y aplica el paso 4.
   - En el alta, la búsqueda por email usa `SELECT ... FOR UPDATE` (bloqueo pesimista). Así, varios recontactos simultáneos de la misma persona se ejecutan en fila en vez de fallar por el control optimista (`@Version`), que de otro modo terminaría en un 500. El bloqueo dura solo la transacción corta del alta. El `@Version` se conserva para el `If-Match` del change 03.

Respuesta: `{ "clienteId": "...", "nuevo": true|false, "coincidencia": "NINGUNA"|"EMAIL" }`. `Coincidencia` reserva `CUIL` para el change 04, que va a buscar primero por CUIL y después por email.

**Por qué la regla de no sobrescribir vive en `Cliente`:** se puede testear sin Spring, y ningún servicio de aplicación futuro la puede saltear. Aunque ahora el alta la llame un servicio autenticado, se conserva como defensa en profundidad: el email de una persona lo puede tipear cualquiera en el preformulario.
**Por qué email y no teléfono:** el email es obligatorio en el contrato y casi nunca lo comparten dos personas. El teléfono es opcional y en una familia es común que se comparta.
**Normalización mínima del email:** no se quitan puntos ni sufijos `+tag` de Gmail. Esas reglas dependen de cada proveedor y podrían unir a personas distintas.
**Alternativa descartada:** no deduplicar, como sugiere el contrato para el MVP. Partiría la ficha y el historial de calificaciones, y un reintento de `leads` crearía un cliente duplicado.

### D5. Normalización del teléfono (value object `Telefono`, §5.6)

- libphonenumber con región por defecto `AR` (`clientes.telefono.region-por-defecto`).
- Si el número argentino resultante no es móvil, se inserta el `9` después de `+54` y se vuelve a validar. Si sigue sin ser válido → 422 `TELEFONO_INVALIDO`, con el campo `telefono` en `errores`.
- Si el teléfono no viene, o viene en blanco, se trata como ausente y no es un error.
- Casos de prueba: la tabla de §5.6.
- En logs se usa la forma enmascarada (`Enmascarador`, change 01). En las respuestas va completo, porque el telemarketer lo necesita.

### D6. Búsqueda por nombre sin acentos: `nombre_busqueda`

- `NombreBusqueda.de(nombre, apellido)`: une los dos con un espacio, aplica `Normalizer` NFD, quita las marcas (`\p{M}`), pasa a minúsculas (`Locale.ROOT`) y deja un solo espacio entre palabras. Ejemplo: `"  María José", "PEÑA"` → `"maria jose pena"`. Que `ñ` se convierta en `n` es una decisión aceptada.
- `Cliente` lo recalcula cada vez que cambian `nombre` o `apellido`. El change 03 lo obtiene sin trabajo extra.
- `q` se normaliza con la misma función, se escapan `\`, `%` y `_`, y se consulta con `nombre_busqueda LIKE '%' || :q || '%' ESCAPE '\'`.
- **Alternativas descartadas:**
  - `unaccent` de Postgres: requiere `CREATE EXTENSION`, es decir, privilegios sobre la base.
  - `ILIKE` sobre `nombre`: no ignora acentos, y "Lopez" no encontraría "López".
- **Escalado:** índice GIN con `pg_trgm` sobre `nombre_busqueda` (§5.11). Como la columna ya está normalizada, no hace falta un índice de expresión.

### D7. Consulta por ID con `ETag`

- `GET /clientes/{id}` devuelve la ficha y un `ETag` débil con la versión (`W/"3"`), que va a usar el `If-Match` del change 03.
- Un ID inexistente, o un cliente que no está `ACTIVO`, responde 404 `CLIENTE_NO_ENCONTRADO`. En este change no se puede anonimizar, y el 03 decide entre 410 y 404.
- La ficha incluye: `id`, `nombre`, `apellido`, `email`, `telefono`, `telefonoAlternativo`, `provincia`, `localidad`, `canalOrigen`, `ultimoCanal`, `contactosRegistrados`, `ultimoContactoEn`, `requiereRevision`. `cuil` y `consentimientosVigentes` se agregan en el 04; sumar campos no rompe a los consumidores.

### D8. Búsqueda paginada

- `GET /clientes?telefono=&email=&q=&provincia=&page=&size=`, solo sobre clientes `ACTIVO`. Los filtros se combinan con AND y sin filtros devuelve todos los activos.
- `telefono`: se normaliza con `Telefono` y se compara exacto con `telefono_e164`. Si no se puede normalizar → 422 `TELEFONO_INVALIDO`.
- `email`: se normaliza y se compara exacto (usa `ux_cliente_email_activo`).
- `q`: según D6; si queda vacío después de normalizar, se ignora.
- `provincia`: igualdad con el enum; un valor fuera del enum → 422 `VALIDACION`.
- Orden: `ultimoContactoEn` descendente y `id` como desempate, para que la paginación sea estable.
- `page` empieza en 0 y por defecto vale 0. `size` vale 20 por defecto y se limita a `clientes.paginacion.tamanio-maximo` (50) sin avisar. `page < 0` o `size < 1` → 422 `VALIDACION`.
- Respuesta: un record propio, no el `Page` serializado de Spring Data (su JSON no es estable entre versiones):

```json
{
  "contenido": [
    { "id": "...", "nombre": "Martina", "apellido": "López", "email": "martina@gmail.com",
      "telefono": "+5491112345678", "provincia": "BUENOS_AIRES", "ultimoCanal": "WHATSAPP",
      "contactosRegistrados": 2, "ultimoContactoEn": "2026-10-05T13:02:44Z" }
  ],
  "pagina": 0, "tamanio": 20, "totalElementos": 1, "totalPaginas": 1
}
```

- Cada elemento es un **resumen**: cuando el change 04 sume el CUIL a la ficha, no va a aparecer en las listas.
- Implementación: un puerto `ClienteRepositorio.buscar(FiltroBusqueda, pagina, tamanio)`, implementado con `Specification` de Spring Data.

### D9. Validación y errores

| Caso | `codigo` | HTTP |
| --- | --- | --- |
| Falta `X-Api-Key` o no coincide en `POST /clientes` | `CLAVE_SERVICIO_INVALIDA` (nuevo) | 401 |
| Bean Validation: `nombre` y `apellido` de 2 a 120 caracteres (recortados; letras, espacios, apóstrofo y guion); `email` obligatorio, hasta 254 caracteres, con formato válido y dominio; `provincia` obligatoria del enum; `canalOrigen` opcional del enum; `localidad` hasta 120 caracteres | `VALIDACION` | 422 |
| Teléfono informado que no se puede normalizar (alta o búsqueda) | `TELEFONO_INVALIDO` (nuevo) | 422 |
| Cuerpo mal formado | `JSON_INVALIDO` | 400 |
| ID inexistente o no activo | `CLIENTE_NO_ENCONTRADO` | 404 |

- El contrato pide "solo letras" en nombre y apellido. Se aceptan también espacios, apóstrofo y guion para no rechazar nombres como "María José", "D'Angelo" o "Pérez-Gómez".
- Un valor fuera del enum en el JSON (por ejemplo `"provincia": "BS AS"`) tiene que responder 422 `VALIDACION` con el campo `provincia`, no 400 `JSON_INVALIDO`. El handler del change 01 tiene que distinguir los dos casos de `HttpMessageNotReadableException`.
- Se propone `errores[{campo, mensaje}]` como formato estándar del equipo, porque el contrato deja pendiente cómo se informan los errores por campo.

### D10. Enums compartidos en `contracts`

- Módulo Maven `contracts`, sin dependencias de Spring, con `Provincia` y `CanalOrigen`. `clientes-service` lo declara como dependencia.
- `Provincia`: BUENOS_AIRES, CABA, CATAMARCA, CHACO, CHUBUT, CORDOBA, CORRIENTES, ENTRE_RIOS, FORMOSA, JUJUY, LA_PAMPA, LA_RIOJA, MENDOZA, MISIONES, NEUQUEN, RIO_NEGRO, SALTA, SAN_JUAN, SAN_LUIS, SANTA_CRUZ, SANTA_FE, SANTIAGO_DEL_ESTERO, TIERRA_DEL_FUEGO, TUCUMAN (24).
- `CanalOrigen`: INSTAGRAM, TIKTOK, MARKETPLACE, FACEBOOK, META_ADS, WHATSAPP, REFERIDO, OTRO.
- Es la lista que se ofrece al equipo para el select de provincia del front y para `leads` y `calificacion` (AC-06).

### D11. Configuración

```yaml
clientes:
  seguridad:
    clave-leads: ${CLIENTES_CLAVE_LEADS}   # mínimo 32 caracteres; ej. openssl rand -base64 32
  telefono:
    region-por-defecto: AR
  paginacion:
    tamanio-maximo: 50
    tamanio-por-defecto: 20
```

### D12. Escalabilidad (§5.11)

- El servicio no tiene estado, así que puede correr en varias réplicas.
- El alta es idempotente por email: los reintentos de `leads` no duplican clientes.
- Índices parciales sobre los clientes activos.
- Paginación obligatoria con tope.
- La clave de servicio se valida localmente, sin llamadas externas.

## Risks / Trade-offs

- **[Un reintento de `leads` por timeout cuenta como un contacto más]** → Compromiso aceptado. "Idempotente" significa acá que no duplica fichas, no que el contador quede igual. Evitarlo exigiría un `Idempotency-Key` y una tabla adicional. La carrera concurrente (D4, paso 5) también suma 1, como dice §5.7.
- **[Al fallar el insert, la transacción queda marcada para rollback]** → Cada intento corre en su propia transacción y el reintento empieza una nueva (D4, paso 5).
- **[Dos personas comparten un email (por ejemplo, una pareja)]** → Se tratan como el mismo cliente. Con el CUIL, en el change 04, un CUIL distinto marca `requiere_revision`.
- **[Alguien conoce el email de otra persona y lo usa en el preformulario]** → El recontacto no pisa nombre, apellido ni provincia; como mucho completa campos vacíos.
- **[`leads` todavía no manda `provincia`, `apellido` ni la clave]** → Sus altas fallan con 401 o 422 hasta que se adapte. Hay que coordinarlo con su responsable antes de integrar (ver `proposal.md`, Impact).
- **[Filtración de la clave de servicio]** → Está en una variable de entorno (no versionada), no se loguea y se rota cambiando la variable. En el Integrador se reemplaza por JWT.
- **[El email es un dato personal y ahora es la clave]** → Los logs de la aplicación referencian el `clienteId`, nunca el email. El change 03 tiene que anular `email` y reescribir `nombre_busqueda` al anonimizar.
- **[Búsqueda por teléfono solo sobre el principal]** → `telefono_alternativo` no se busca en este change. Se puede agregar con su propio índice si el telemarketer lo necesita.
- **[`LIKE '%q%'` no usa índice]** → Alcanza para el volumen de referencia; si crece, `pg_trgm` (D6).
- **[El teléfono completo aparece en las respuestas]** → Decisión de negocio (§5.6). En los logs siempre va enmascarado.

## Migration Plan

- Flyway aplica `V1__cliente.sql` al arrancar sobre `clientes_db`. No hay datos previos.
- Hay que agregar `CLIENTES_CLAVE_LEADS` a `infra/.env` y a docker-compose antes de levantar el servicio; sin ella el arranque falla a propósito.
- Rollback: revertir el PR. Al ser la primera migración, en los entornos de laboratorio se puede recrear la base.
