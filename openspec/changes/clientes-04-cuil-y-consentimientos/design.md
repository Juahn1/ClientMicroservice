# Design: CUIL y consentimiento de precalificación

> Fuentes: `docs/specs-seed/clientes-service-spec.md` (§5.2, §5.6, §5.7, §5.8 y §6.4) y el contrato del equipo `contratos-backend.md` (v1.1).
> Motivación y alcance: ver `proposal.md`. Requisitos: ver `specs/clientes-privacidad/spec.md`, `specs/clientes-gestion/spec.md` y `specs/clientes-eventos/spec.md`.

## Context

- Los changes 01, 02 y 03 están archivados. Ya existen:
  - `clientes.cuil.clave-cifrado` y `clientes.cuil.clave-hash`, validadas al arrancar por `ClientesProperties.Cuil` (base64, 32 bytes), con claves ficticias fijas en el perfil local y `:?` obligatorio en `infra/docker-compose.yml`;
  - `Enmascarador.cuil` y `EnmascararDatosPersonalesConverter`, que enmascaran CUIL y teléfono en los logs;
  - el paquete vacío `infrastructure/crypto`;
  - las columnas `requiere_revision` y `motivo_revision` (V1), que hasta ahora nadie escribe.
- `AltaClienteService` busca por email con `FOR UPDATE` y reintenta una vez si choca contra `ux_cliente_email_activo` (`EmailDuplicadoException`, que `ClienteRepositorioJpa.guardar` detecta por el nombre del índice). `EdicionClienteService` usa la misma excepción para el 409 `EMAIL_EN_USO`.
- `Cliente` está separado de `ClienteEntity`. `aEntidad` arma una entidad nueva en cada guardado y `saveAndFlush` hace merge.
- `Cliente.registrarRecontacto` ya completa el teléfono y manda el distinto a `telefonoAlternativo` si estaba vacío.
- Los errores de dominio asociados a un campo (`TelefonoInvalidoException`) se traducen con `conErrorDeCampo`, que les agrega `errores[{campo, mensaje}]`.
- El design D6 del change 03 previó que cada change futuro limpiara lo suyo al anonimizar con un listener `BEFORE_COMMIT`.
- Migración siguiente: V3 (V2 la usó el change 03).

## Goals / Non-Goals

**Goals:**
- El CUIL en claro existe solo en memoria durante el request, nunca en la base, en las respuestas ni en los logs.
- Invariante CUIL ⇔ consentimiento de precalificación vigente, garantizada en un solo lugar.
- Un algoritmo de alta determinístico y seguro ante concurrencia con dos claves únicas (email y CUIL).
- Modelo de datos definitivo: los endpoints del backlog (`PUT /cuil`, `busqueda-por-cuil`, otorgar y revocar consentimientos, `CONTACTO_COMERCIAL`) se suman sin migrar datos.

**Non-Goals:**
- Descifrar el CUIL: en este change nadie lo necesita. El 05 lo descifra para consultar la fuente.
- Rotación de claves: cambiar `clave-hash` invalida todos los `cuil_hash`, y cambiar `clave-cifrado` vuelve ilegibles los cifrados. Se documenta como riesgo.
- Vaciar el teléfono alternativo por PATCH (un null no modifica, igual que en el 03).

## Decisions

### D1. Estructura de paquetes

```text
com.uade.crm.clientes
├── api
│   ├── dto              AltaClienteRequest (+ cuil, consentimiento), EdicionClienteRequest (+ telefonoAlternativo,
│   │                    requiereRevision), ClienteResponse (+ cuil, consentimientosVigentes, motivoRevision),
│   │                    ConsentimientoResponse
│   └── error            CodigoError (+ CUIL_INVALIDO, CONSENTIMIENTO_REQUERIDO), ApiExceptionHandler
├── application          AltaClienteService (algoritmo D5), EdicionClienteService
├── domain
│   ├── ClienteFactory   + CifradorCuil; valida CUIL + consentimiento y lo protege
│   ├── model            DatoInvalidoException (422 VALIDACION con campo), ConsentimientoRequeridoException
│   ├── model            Cliente (+ cuil, consentimientos, registrarCuil, marcarRevision, resolverRevision),
│   │                    Consentimiento, FinalidadConsentimiento, OrigenConsentimiento, MotivoRevision,
│   │                    Coincidencia (+ CUIL), SolicitudAlta (+ cuil), CambiosCliente (+ 2 campos)
│   ├── valor            Cuil, CuilInvalidoException, CuilProtegido
│   └── port             CifradorCuil, ClienteRepositorio (+ buscarActivoPorCuilParaActualizar),
│                        ClaveDuplicadaException (reemplaza a EmailDuplicadoException)
├── infrastructure
│   ├── crypto           AesGcmCifradorCuil
│   └── persistence      ClienteEntity (+ cuil_*, consentimientos), ConsentimientoEntity
└── config               ClientesProperties (+ consentimiento.versionTexto)
```

### D2. `Cuil` y `CuilProtegido`: validar en el dominio, proteger detrás de un puerto

- `Cuil` (value object): recibe el texto, quita guiones y espacios, y exige 11 dígitos, un prefijo de persona humana (20, 23, 24, 27) y el verificador por módulo 11 (§5.6 del seed). Si falla, lanza `CuilInvalidoException`. Su `toString()` devuelve la forma enmascarada.
- `CifradorCuil` (puerto de dominio) convierte un `Cuil` en `CuilProtegido(byte[] cifrado, String hash, String ultimos4)`:
  - `cifrado`: AES-256-GCM, IV aleatorio de 12 bytes antepuesto, tag de 128 bits;
  - `hash`: HMAC-SHA256 en hexadecimal (64 caracteres) de los 11 dígitos;
  - `ultimos4`: dígitos 7 a 10, que son los últimos 4 del DNI.
- `AesGcmCifradorCuil` (infraestructura) implementa el puerto con `javax.crypto`. No hace falta ninguna dependencia nueva. Las claves vienen de `ClientesProperties.Cuil`.
- El método `descifrar` **no** se agrega todavía: lo suma el 05, que es el primero que lo usa. Así, en este change no hay ningún camino de código que reconstruya el CUIL.
- `Cliente` solo conoce `CuilProtegido`, y el enmascarado sale de `ultimos4` sin descifrar nada.

**Por qué un puerto y no cifrar con un `AttributeConverter` de JPA:** el hash se necesita **antes** de persistir (para buscar por CUIL en la deduplicación), así que el dominio tiene que poder calcularlo. Un converter solo actúa al leer y escribir la columna, y además dejaría el CUIL en claro dentro de la entidad.

### D3. Consentimiento dentro del agregado `Cliente`

- `Cliente` tiene `List<Consentimiento>`. `Consentimiento` (id UUID generado en el dominio, finalidad, versionTexto, origen, otorgadoEn, revocadoEn) es una entidad interna del agregado.
- La invariante vive en `Cliente`:
  - `registrarCuil(CuilProtegido, versionTexto, ahora)`: exige que no tenga CUIL; guarda el CUIL y agrega el consentimiento `PRECALIFICACION_CREDITICIA` / `PREFORMULARIO`, salvo que ya tenga uno vigente, en cuyo caso conserva el existente;
  - `anonimizar(ahora)`: además de lo del 03, borra `cuil` y revoca los consentimientos vigentes con `revocadoEn = ahora`.
- `FinalidadConsentimiento` es un enum local con un valor, `PRECALIFICACION_CREDITICIA`. `OrigenConsentimiento` también tiene un valor por ahora, `PREFORMULARIO`. Las columnas son `VARCHAR`, así que sumar valores no requiere migración.
- **Persistencia:** `ClienteEntity` tiene `@OneToMany(cascade = ALL)` unidireccional con `@JoinColumn(name = "cliente_id")` hacia `ConsentimientoEntity`. Como el id del consentimiento se genera en el dominio, el merge de `aEntidad` actualiza las filas existentes en lugar de duplicarlas. Al ser el cliente el dueño de la colección, los cambios en ella incrementan su `@Version`.
- **Desvío del D6 del change 03:** la limpieza al anonimizar **no** usa un listener `BEFORE_COMMIT`, porque CUIL y consentimientos son parte del agregado y `anonimizar()` los alcanza directamente. El listener sigue siendo el mecanismo para los módulos que viven fuera del agregado (la caché crediticia del 05 y el historial).

**Alternativa descartada:** un repositorio propio para `Consentimiento`. La invariante quedaría repartida entre dos servicios de aplicación, y el día que exista la revocación habría que coordinarlos a mano.

### D4. Request y validación de `POST /clientes`

- `AltaClienteRequest` suma `cuil` (String, `@Size(max = 20)`) y `consentimiento` (Boolean). Se sobrescribe `toString()` para que el record no imprima el CUIL.
- `ClienteFactory.solicitud(...)` (que ahora recibe `CifradorCuil` y la versión del texto) aplica la tabla de B3:

  | cuil | consentimiento | resultado |
  |---|---|---|
  | ausente | ausente / false | `SolicitudAlta` sin CUIL |
  | ausente | true | `ValidacionException` de campo `consentimiento` → 422 `VALIDACION` |
  | inválido | cualquiera | `CuilInvalidoException` → 422 `CUIL_INVALIDO`, campo `cuil` |
  | válido | ausente / false | `ConsentimientoRequeridoException` → 422 `CONSENTIMIENTO_REQUERIDO`, campo `consentimiento` |
  | válido | true | `SolicitudAlta` con `CuilProtegido` |

- El orden importa: el CUIL se valida antes que el consentimiento, para que un CUIL mal tipeado reporte el error útil.
- `SolicitudAlta.cuil` presente implica que el consentimiento fue otorgado, así que no hace falta un booleano aparte.
- Hoy no existe una excepción de dominio para un 422 `VALIDACION` asociado a un campo. Se agrega `DatoInvalidoException(campo, mensaje)` en `domain`, que el handler traduce con `conErrorDeCampo`. La usan el consentimiento sin CUIL (campo `consentimiento`) y el teléfono alternativo igual al principal (D7), porque esta última regla depende del estado del cliente y no se puede expresar con Bean Validation. En cambio, `requiereRevision = true` sí se rechaza con `@AssertFalse` en el DTO.

### D5. Algoritmo de alta

```text
registrar(solicitud):                       // cada intento en su propia transacción (como hoy)
  si solicitud.cuil:
    A = buscarActivoPorCuilParaActualizar(hash)             // FOR UPDATE
    si A:
      B = buscarActivoPorEmail(email)                       // sin bloqueo: B no se modifica
      A.registrarRecontacto(solicitud)
      si B y B != A: A.marcarRevision(POSIBLE_DUPLICADO)
      guardar(A) -> 200, CUIL
  E = buscarActivoPorEmailParaActualizar(email)             // FOR UPDATE
  si E:
    E.registrarRecontacto(solicitud)
    si solicitud.cuil:
      si E sin CUIL: E.registrarCuil(...)
      si E con otro CUIL: E.marcarRevision(CUIL_DISTINTO)   // CUIL y consentimiento descartados
    guardar(E) -> 200, EMAIL
  crear(solicitud) -> 201, NINGUNA                          // con CUIL y consentimiento si vienen
```

- "Otro CUIL" se compara por `hash`. Si E tiene el **mismo** hash, el caso ya se resolvió en la rama de CUIL.
- `marcarRevision(motivo)` no hace nada si ya está marcado: conserva el primer motivo.
- `registrarRecontacto` se mantiene: no pisa nombre, apellido, provincia ni email; completa lo vacío y quita `noContactar`.
- **Concurrencia:** `ClienteRepositorioJpa.guardar` traduce las violaciones de `ux_cliente_email_activo` y de `ux_cliente_cuil_activo` a `ClaveDuplicadaException(campo)`, con `campo` = `EMAIL` o `CUIL`. `AltaClienteService` reintenta una vez ante cualquiera de las dos, y el reintento cae en la rama de coincidencia. `EdicionClienteService` traduce a 409 `EMAIL_EN_USO` solo si `campo == EMAIL`, porque la edición no toca el CUIL.
- **Sin deadlock:** cada intento toma como máximo un bloqueo de fila (A o E), y B solo se lee.

**Por qué CUIL antes que email:** el CUIL identifica a la persona, y el email puede cambiar o compartirse (por ejemplo, en una pareja). Si se buscara primero por email, un mismo CUIL con un email nuevo intentaría crear un segundo cliente y chocaría contra el índice único de CUIL.

### D6. V3: `V3__cuil_y_consentimiento.sql`

```sql
ALTER TABLE cliente
    ADD COLUMN cuil_cifrado  BYTEA,
    ADD COLUMN cuil_hash     VARCHAR(64),
    ADD COLUMN cuil_ultimos4 VARCHAR(4);
CREATE UNIQUE INDEX ux_cliente_cuil_activo ON cliente (cuil_hash) WHERE estado = 'ACTIVO';
ALTER TABLE cliente
    ADD CONSTRAINT ck_cliente_cuil_completo
        CHECK ((cuil_cifrado IS NULL) = (cuil_hash IS NULL) AND (cuil_hash IS NULL) = (cuil_ultimos4 IS NULL));
-- ck_cliente_anonimizado_sin_datos se reemplaza (DROP + ADD) sumando "cuil_cifrado IS NULL";
-- las otras dos columnas caen por ck_cliente_cuil_completo.

CREATE TABLE consentimiento (
    id            UUID PRIMARY KEY,
    cliente_id    UUID        NOT NULL REFERENCES cliente(id),
    finalidad     VARCHAR(40) NOT NULL,
    version_texto VARCHAR(20) NOT NULL,
    origen        VARCHAR(20) NOT NULL,
    otorgado_en   TIMESTAMPTZ NOT NULL,
    revocado_en   TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_consentimiento_vigente ON consentimiento (cliente_id, finalidad) WHERE revocado_en IS NULL;
```

- La invariante CUIL ⇔ consentimiento vigente **no** se expresa en SQL, porque cruza dos tablas y requeriría un trigger. La garantiza el agregado (D3), y la cubre un test de repositorio.
- `ClienteResumenResponse` y `ClienteSpecs` no cambian: la búsqueda no conoce el CUIL.

### D7. Ficha y PATCH

- `ClienteResponse` suma:
  - `cuil`: `"XX-XXXX" + ultimos4 + "-X"`, o null; se agrega `Enmascarador.cuilDesdeUltimos4` para no duplicar el formato;
  - `consentimientosVigentes`: `List<ConsentimientoResponse(finalidad, versionTexto, otorgadoEn)>`;
  - `motivoRevision`.
- `EdicionClienteRequest` suma `telefonoAlternativo` (mismas reglas que `telefono`) y `requiereRevision` (Boolean). `CambiosCliente` y `Cliente.editar` los aplican:
  - `telefonoAlternativo` igual al principal (el actual o el que llega en el mismo PATCH) → `DatoInvalidoException` → 422 `VALIDACION`, campo `telefonoAlternativo`;
  - `requiereRevision = true` → 422 `VALIDACION` por `@AssertFalse`; `false` → `resolverRevision()`, que no hace nada si no estaba marcado.
- `Cliente.editar` hoy devuelve el `Set<CampoCliente>` que alimenta el evento. Como estos dos campos no están en `CampoCliente` (y no deben estar: D8), `editar` pasa a devolver un resultado con dos datos: si hubo algún cambio, que define si se guarda y cambia el `ETag`, y los campos publicables, que definen si hay evento.

### D8. Sin eventos nuevos

- `CampoCliente` (en `contracts`) no cambia. Registrar el CUIL o el consentimiento, marcar o resolver la revisión y cambiar el teléfono alternativo no publican nada: `leads` no copia esos datos, y el contrato prohíbe el CUIL en los eventos.
- **Nota para el 05:** en este change el CUIL de un cliente nunca cambia una vez registrado. Solo se registra si no tenía, y solo se borra al anonimizar. La caché crediticia del 05 no necesita invalidarse por cambio de CUIL hasta que exista `PUT /cuil` (backlog).

### D9. Logs

- El converter existente ya enmascara cualquier secuencia con forma de CUIL. Además:
  - `Cuil`, `CuilProtegido`, `SolicitudAlta` y `AltaClienteRequest` tienen `toString()` sin el CUIL;
  - `CuilProtegido.toString()` tampoco imprime el hash, porque un hash de un espacio de 11 dígitos con clave filtrada es reversible por fuerza bruta.
- `DatosPersonalesEnLogsTest` se amplía con un alta con CUIL en nivel DEBUG.

## Risks / Trade-offs

- **[Fuga o pérdida de las claves]** → Si se filtra `clave-hash`, el hash es reversible por fuerza bruta (unos 10⁹ candidatos válidos). Si se pierde `clave-cifrado`, los CUIL cifrados no se recuperan. Mitigación: claves solo por variable de entorno, sin valor por defecto fuera del perfil local, y validadas al arrancar. La rotación queda fuera de alcance y se documenta en el README.
- **[El email nuevo se descarta en una coincidencia por CUIL]** → Si el email registrado ya no sirve, se pierde el canal. Mitigación: el teléfono nuevo sí se guarda como alternativo, y `email_alternativo` es una columna aditiva del backlog si el problema aparece.
- **[Un solo reintento con dos claves únicas]** → En teoría, el reintento podría chocar contra la otra clave si hay tres altas simultáneas de la misma persona con combinaciones cruzadas. En ese caso la segunda `ClaveDuplicadaException` se propaga como 500 y `leads` reintenta. Es aceptable por lo improbable.
- **[Una sola columna de motivo]** → Si se acumulan motivos, se ve solo el primero. El telemarketer revisa la ficha completa de todos modos, y la fusión de duplicados (`clientes-12`) traería una tabla de motivos.
- **[`leads` no valida "financiación ⇒ CUIL"]** → Si se olvida, llegan leads con financiación sin CUIL. `clientes` no lo puede detectar, porque no conoce `solicitaFinanciacion`. Mitigación: se deja explícito en el texto para el contrato del equipo.

## Migration Plan

1. Flyway aplica V3 al arrancar. Todas las columnas nuevas son nullables y la tabla `consentimiento` es nueva, así que los clientes existentes quedan sin CUIL y sin consentimientos, y son válidos.
2. Si `leads` todavía no envía `cuil`, todo funciona igual que en el 02 y el 03.
3. **Rollback:** la versión anterior del servicio ignora las columnas nuevas. `ddl-auto: validate` no falla por columnas extra, y no hay datos que convertir.
