# Design: Situación crediticia

> Fuentes: `docs/specs-seed/clientes-service-spec.md` (§3 AC-05, §3.1 S-01, §5.2, §5.9, §5.10 y §6.5) y el contrato del equipo `contratos-backend.md` (v1.1), que sugiere decorators sobre el puerto de precalificación.
> Motivación y alcance: ver `proposal.md`. Requisitos: ver `specs/clientes-situacion-crediticia/spec.md`, `specs/clientes-transversal/spec.md` y `specs/clientes-gestion/spec.md`.

## Context

- Los changes 01 a 04 están archivados. Ya existen:
  - `ClientesProperties.SituacionCrediticia` (`modo`, `vigencia`, `timeout`, `simulado.demora`, `simulado.forzarNoDisponible`) con sus valores en `application.yml`, el enum `ModoSituacionCrediticia` (`SIMULADO`, `BCRA`) y la instancia `situacionCrediticia` del circuit breaker en `resilience4j.circuitbreaker.instances`;
  - la dependencia `resilience4j-spring-boot3` y el paquete vacío `infrastructure/precalificacion`;
  - `CifradorCuil` con solo `proteger`; `AesGcmCifradorCuil` guarda el IV de 12 bytes antepuesto al texto cifrado;
  - `ClaveServicioInterceptor` con una única clave (`claveLeads`) y `@RequiereClaveServicio` sin parámetros, usada solo en `POST /clientes`;
  - `AnonimizacionClienteService` toma el cliente con `buscarPorIdParaActualizar` (`FOR UPDATE`) y publica `ClienteAnonimizadoEvento` dentro de la transacción;
  - un único `Clock` (`DominioConfig`) y la anotación de tests `@PruebaIntegracion`, que comparte un solo contexto de Spring entre todas las clases de integración.
- Por la invariante del agregado (`Cliente`, design D3 del change 04), un cliente activo con CUIL siempre tiene vigente el consentimiento de precalificación, y el CUIL no cambia una vez registrado.
- El change 04 encontró que `ddl-auto: validate` rechaza columnas `CHAR(n)` mapeadas a `String`: las columnas de texto nuevas van como `VARCHAR`.
- Migración siguiente: V4.

## Goals / Non-Goals

**Goals:**
- La consulta nunca devuelve 5xx por la fuente y tarda como máximo el timeout más el acceso a la base.
- La fuente se reemplaza en la Segunda Parte (SOAP contra `precalificacion`) cambiando solo el último eslabón de la cadena, sin tocar la caché, la resiliencia, el servicio ni la API.
- El CUIL descifrado vive solo en memoria y solo durante la llamada a la fuente.
- Ninguna transacción de base queda abierta mientras se espera a la fuente.

**Non-Goals:**
- La rama `SIN_CONSENTIMIENTO` del servicio: con la invariante del 04 es código inalcanzable y no se puede testear. La agrega `clientes-15` junto con la revocación.
- Precargar la caché después de un alta (`clientes-09`).
- Un bulkhead que limite las llamadas simultáneas a la fuente: con unas decenas de consultas por día, caché de 30 días y circuit breaker no protege nada. Si hiciera falta, un `SemaphoreBulkhead` de Resilience4j se suma al decorator de resiliencia sin hilos extra.

## Decisions

### D1. Estructura de paquetes

```text
com.uade.crm.clientes
├── api
│   ├── SituacionCrediticiaController   GET /clientes/{id}/situacion-crediticia, @RequiereClaveServicio(CALIFICACION)
│   ├── dto                             SituacionCrediticiaResponse
│   └── seguridad                       Consumidor (LEADS, CALIFICACION), RequiereClaveServicio(Consumidor),
│                                       ClaveServicioInterceptor (una clave por consumidor)
├── application
│   ├── SituacionCrediticiaService      404/410, SIN_CUIL, descifrar, llamar al puerto
│   └── listener                        SituacionCrediticiaAnonimizacionListener (BEFORE_COMMIT)
├── domain
│   ├── model                           SituacionCrediticia, FuenteSituacion (SIMULADA, BCRA)
│   └── port                            SituacionCrediticiaPort, ConsultaSituacion, FuenteNoDisponibleException,
│                                       CacheSituacionCrediticia, CifradorCuil (+ descifrar)
├── infrastructure
│   ├── crypto                          AesGcmCifradorCuil (+ descifrar)
│   ├── persistence                     SituacionCrediticiaEntity, CacheSituacionCrediticiaJpa
│   └── precalificacion                 SituacionCrediticiaConCache, SituacionCrediticiaResiliente,
│                                       SimuladaSituacionAdapter, PrecalificacionConfig (arma la cadena)
└── config                              ClientesProperties (+ seguridad.claveCalificacion)

com.uade.crm.contracts.clientes         EstadoSituacionCrediticia
```

### D2. Servicio arriba, decorators abajo

```text
SituacionCrediticiaService
  buscarPorId ──no──> ClienteNoEncontradoException (404)
  anonimizado ──────> ClienteAnonimizadoException (410)
  sin CUIL ─────────> SituacionCrediticia.sinCuil()        (no llama al puerto)
  descifrar(cuil) ──> puerto.consultar(ConsultaSituacion(clienteId, cuil))
                         │
          SituacionCrediticiaConCache          lee la caché vigente; si no hay, delega y guarda CON_DATOS/SIN_REGISTROS
                         │
          SituacionCrediticiaResiliente        circuit breaker; toda falla → SituacionCrediticia.noDisponible()
                         │
          SimuladaSituacionAdapter             tabla §5.10; respeta el timeout (D5); lanza FuenteNoDisponibleException
                                               si se fuerza la falla o vence el timeout
```

- Las reglas que necesitan el agregado (existencia, estado, CUIL) quedan en el servicio. La caché y la resiliencia son responsabilidades agregadas a la misma interfaz y no saben nada del cliente: por eso son decorators (patrón que también sugiere el contrato del equipo).
- **Orden:** la caché va afuera de la resiliencia. Un acierto de caché no cuenta como llamada exitosa para el circuit breaker (diluiría la tasa de fallas), y un `NO_DISPONIBLE` producido por la resiliencia llega a la caché como resultado, que lo descarta.
- El puerto recibe `ConsultaSituacion(UUID clienteId, Cuil cuil)`. La fuente solo usa el CUIL; el `clienteId` lo necesita la caché, cuya clave es `cliente_id` (así la borra la anonimización). Es una concesión explícita: la alternativa de usar `cuil_hash` como clave obliga a pasar el hash al puerto y a borrar por hash al anonimizar. `toString()` de `ConsultaSituacion` no imprime el CUIL.
- Todos los eslabones implementan `SituacionCrediticiaPort`. `PrecalificacionConfig` los arma a mano (`new ...Cache(new ...Resiliente(fuente, ...), ...)`) y expone solo la cadena como bean del puerto; la fuente es otro bean, calificado como `fuenteSituacionCrediticia`, que los tests de integración reemplazan (D10).
- **Alternativa descartada:** que el servicio orqueste la caché y Resilience4j envuelva solo el puerto (seed §6.5). Tiene menos clases, pero el servicio pasa de 3 ramas a 7 y la regla "no cachear `NO_DISPONIBLE`" se mezcla con las reglas del cliente.
- **Alternativa descartada:** un decorator solo para logs. Cada eslabón loguea lo suyo (D8) y otra clase no aporta nada.

### D3. Modelo y contrato de la respuesta

- `EstadoSituacionCrediticia` (en `contracts`, paquete `clientes`): `CON_DATOS`, `SIN_REGISTROS`, `SIN_CUIL`, `SIN_CONSENTIMIENTO`, `NO_DISPONIBLE`. El Javadoc documenta que `SIN_CONSENTIMIENTO` está reservado hasta `clientes-15` y el mapeo de AC-05 para `calificacion`. Solo el enum: un DTO compartido haría que `calificacion` dependa de cómo `clientes` serializa su respuesta.
- `SituacionCrediticia` (record del dominio): `estado`, `situacion` (`Integer`), `periodo` (`YearMonth`), `fuente` (`FuenteSituacion`), `consultadaEn` (`Instant`). Fábricas `conDatos`, `sinRegistros`, `sinCuil`, `noDisponible`; el constructor valida que `situacion` esté entre 1 y 6 si y solo si el estado es `CON_DATOS`, y que `periodo`, `fuente` y `consultadaEn` estén si y solo si el estado es `CON_DATOS` o `SIN_REGISTROS`. `esCacheable()` es true para esos dos estados.
- `SituacionCrediticiaResponse`: `clienteId`, `estado`, `situacion`, `periodo` (`"AAAAMM"`), `fuente`, `consultadaEn`. Los nulos se serializan como `null` (la forma no cambia según el estado; `calificacion` lee siempre los mismos campos).
- `FuenteSituacion` es local al servicio: `calificacion` no la necesita para puntuar.

### D4. Caché en base (S-01)

```sql
-- V4__situacion_crediticia.sql
CREATE TABLE situacion_crediticia (
    cliente_id     UUID        PRIMARY KEY REFERENCES cliente (id),
    estado         VARCHAR(20) NOT NULL,
    situacion      SMALLINT,
    periodo        VARCHAR(6)  NOT NULL,          -- AAAAMM
    fuente         VARCHAR(20) NOT NULL,
    consultada_en  TIMESTAMPTZ NOT NULL,
    vigente_hasta  TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_situacion_estado CHECK (estado IN ('CON_DATOS', 'SIN_REGISTROS')),
    CONSTRAINT ck_situacion_valor  CHECK ((estado = 'CON_DATOS') = (situacion IS NOT NULL)
                                         AND (situacion IS NULL OR situacion BETWEEN 1 AND 6))
);
```

- Solo los estados cacheables pueden estar en la tabla: la base lo garantiza aunque el código se equivoque.
- `vigente_hasta = consultada_en + vigencia` se calcula al guardar. Cambiar `vigencia` afecta solo a los resultados nuevos; los guardados vencen con la vigencia con la que se consultaron.
- Puerto `CacheSituacionCrediticia` en el dominio: `Optional<SituacionCrediticia> vigente(UUID clienteId, Instant ahora)`, `guardar(UUID clienteId, SituacionCrediticia, Instant vigenteHasta)` y `eliminar(UUID clienteId)`. El adaptador JPA vive en `infrastructure/persistence`.
- **Guardado concurrente y con el cliente activo.** `guardar` corre en su propia transacción corta y hace, en una sola sentencia:

  ```sql
  INSERT INTO situacion_crediticia (...)
  SELECT :clienteId, ... FROM cliente WHERE id = :clienteId AND estado = 'ACTIVO' FOR SHARE
  ON CONFLICT (cliente_id) DO UPDATE SET ... ;
  ```

  `ON CONFLICT` resuelve dos consultas simultáneas sin resultado guardado (gana la última, y las dos traen el mismo dato de la fuente). `FOR SHARE` sobre la fila del cliente evita una carrera con la anonimización: si la anonimización tiene tomada la fila (`FOR UPDATE`), el guardado espera, ve `ANONIMIZADO` y no inserta nada; si el guardado llegó primero, la anonimización espera a que confirme y su `DELETE` lo encuentra. Si al implementar Postgres no acepta `FOR SHARE` en `INSERT ... SELECT`, el mismo efecto se logra con un `SELECT ... FOR SHARE` previo en la misma transacción.
- La lectura de la caché no bloquea ni abre transacción larga: el servicio no es `@Transactional`, así que mientras se espera a la fuente no hay conexión tomada.

### D5. Resiliencia: el timeout es obligación de la fuente, el circuit breaker es un decorator

**Timeout en el adaptador.** `SituacionCrediticiaPort` documenta un contrato para toda fuente: responder o lanzar `FuenteNoDisponibleException` dentro de `clientes.situacion-crediticia.timeout` (la causa, si la hay, va como `cause`). Cada adaptador lo cumple con el mecanismo natural de su tecnología:

- el simulado espera como mucho el timeout (D6);
- el SOAP de `clientes-09` configura los timeouts de conexión y de lectura de su cliente HTTP con la misma propiedad.

Todo corre en el hilo del request: sin pool propio, sin copiar el MDC (el `correlationId` ya está en el hilo) y sin rechazos que manejar.

- **Por qué no un executor con `Future.get(timeout)`** (la primera versión de este design): con la fuente real no libera nada. `cancel(true)` interrumpe el hilo, pero los clientes HTTP bloqueantes clásicos (`HttpURLConnection`, el transporte por defecto de JAX-WS) no se desbloquean con una interrupción mientras leen del socket: el request volvería a tiempo y el hilo del pool quedaría bloqueado hasta el timeout del socket, que el adaptador SOAP tiene que configurar igual. El executor duplicaba esa responsabilidad, costaba dos hilos por consulta, la copia del MDC y el manejo de una cola llena, y solo cortaba de verdad al simulador. Su otro beneficio, el bulkhead, no protege nada a este volumen (ver Non-Goals).
- **Por qué no un `TimeLimiter` de Resilience4j:** necesita el mismo executor y tiene el mismo problema con la interrupción.
- **Concesiones:**
  - el decorator ya no garantiza el límite: lo garantiza cada adaptador, y lo verifica un test de contrato reutilizable ("con una fuente lenta, falla dentro del timeout"), que `clientes-09` tiene que pasar con el adaptador SOAP;
  - un timeout de lectura cuenta entre lecturas, no el total de la respuesta. Con respuestas SOAP chicas alcanza; si no, `java.net.http.HttpClient` tiene timeout por request.

**Circuit breaker en `SituacionCrediticiaResiliente`.**

- Es la instancia `situacionCrediticia` del `CircuitBreakerRegistry` que crea el starter, configurada en `application.yml`. Se suma `minimum-number-of-calls: 10` (por defecto son 100, más que la ventana, y el circuito no se abriría nunca) y `permitted-number-of-calls-in-half-open-state: 1`, que hace determinístico el "reintento después de la ventana de espera".
- Cuenta como falla `FuenteNoDisponibleException`, sea por caída o por timeout. La traduce a `NO_DISPONIBLE` con log WARN (causa y estado del circuito). Con el circuito abierto, `CallNotPermittedException` también termina en `NO_DISPONIBLE` sin llamar a la fuente.
- Cualquier otra excepción de la fuente (un error de programación) también se traduce a `NO_DISPONIBLE` y cuenta como falla: la consulta nunca responde 5xx por culpa de la fuente.
- **Alternativa descartada:** la anotación `@CircuitBreaker`. Necesita AOP y la traducción a `NO_DISPONIBLE` queda en un método `fallback` resuelto por reflexión. Programáticamente es una llamada a `circuitBreaker.executeSupplier(...)` dentro de un `try`.

### D6. Adaptador simulado y selección por `modo`

- `SimuladaSituacionAdapter` implementa la tabla §5.10 sobre `Cuil.digitos().charAt(9)` (último dígito del DNI). `periodo` = mes anterior a `YearMonth.now(reloj)`; `consultadaEn` = `reloj.instant()`; `fuente` = `SIMULADA`.
- Simula a la vez al servidor lento y a su cliente HTTP con timeout. Con `simulado.demora` menor o igual al timeout, espera la demora y responde. Con una demora mayor, espera solo el timeout y lanza `FuenteNoDisponibleException`, como haría un cliente cuyo timeout de lectura vence. Con `simulado.forzar-no-disponible` lanza la excepción de inmediato. Recibe el timeout en el constructor, desde `clientes.situacion-crediticia.timeout`.
- `PrecalificacionConfig` elige la fuente con un `switch` sobre `modo`: `SIMULADO` crea el adaptador simulado; `BCRA` lanza `IllegalStateException("clientes.situacion-crediticia.modo = bcra todavía no tiene adaptador ...")` y el servicio no arranca. Es más explícito que un `@ConditionalOnProperty` que deja el puerto sin bean y falla con un error genérico de Spring. `clientes-09` reemplaza esa rama.

### D7. Claves de servicio por consumidor

- `RequiereClaveServicio` pasa a tener un `value` obligatorio de tipo `Consumidor` (`LEADS`, `CALIFICACION`). Sin valor por defecto: un endpoint nuevo no puede quedar abierto a la clave equivocada por omisión. `POST /clientes` queda `@RequiereClaveServicio(Consumidor.LEADS)`.
- `ClaveServicioInterceptor` guarda un `Map<Consumidor, byte[]>` y compara en tiempo constante solo contra la clave del consumidor que pide el handler.
- `ClientesProperties.Seguridad` suma `claveCalificacion` con la misma validación que `claveLeads` (no vacía, placeholder resuelto, mínimo 32 caracteres) y además exige que las dos claves sean distintas: si fueran iguales, la separación por consumidor no existiría.
- Con la clave válida, el interceptor deja en el log, en DEBUG, qué consumidor accedió (nunca la clave). Sirve como rastro de las consultas que descifran el CUIL hasta que llegue la auditoría de `clientes-11`.
- Configuración: `clientes.seguridad.clave-calificacion: ${CLIENTES_CLAVE_CALIFICACION}`; en `infra/docker-compose.yml` con `:?` (obligatoria), en `infra/.env.example` con su comentario, con valor ficticio por defecto en `application-local.yml` y fijo en `application-test.yml`.

### D8. CUIL descifrado y logs

- `CifradorCuil` suma `Cuil descifrar(CuilProtegido)`: separa el IV de 12 bytes y descifra con AES-GCM. Una falla (tag inválido, clave distinta) lanza `IllegalStateException` y termina en 500 `ERROR_INTERNO`: no es una falla de la fuente sino un dato propio corrupto o una clave cambiada, y esconderlo como `NO_DISPONIBLE` lo volvería invisible.
- El `Cuil` descifrado solo se pasa al puerto dentro de `ConsultaSituacion`; su `toString()` ya está enmascarado (change 04).
- Logs: la caché registra acierto o consulta a la fuente en DEBUG, con `clienteId`; la resiliencia registra cada falla en WARN con su causa y el estado del circuito; el servicio registra en INFO el `estado` resultante. Ninguno incluye el CUIL. El converter de enmascarado del change 01 sigue como red de seguridad.

### D9. Anonimización

- `SituacionCrediticiaAnonimizacionListener` escucha `ClienteAnonimizadoEvento` con `@TransactionalEventListener(phase = BEFORE_COMMIT)` y llama a `CacheSituacionCrediticia.eliminar(clienteId)` en la misma transacción. Si falla, la anonimización completa se revierte. Es el mecanismo que dejaron previsto el design D6 del 03 y el D3 del 04 para los datos que viven fuera del agregado.
- `AnonimizacionClienteService` no cambia.

### D10. Tests

- **Unitarios sin Spring:** `SituacionCrediticia` (invariantes), adaptador simulado (tabla completa y período con `Clock` fijo), cadena completa armada a mano con un `CircuitBreaker` real de configuración corta, una caché en memoria y una fuente de prueba (caída, circuito abierto, reintento después de la ventana, `NO_DISPONIBLE` no se guarda, vigencia con `Clock` controlable), y `descifrar` en `AesGcmCifradorCuilTest`.
- **Test de contrato de la fuente:** una clase abstracta `ContratoFuenteSituacionTest` con los casos que toda fuente tiene que cumplir: con una demora mayor que el timeout falla con `FuenteNoDisponibleException` en un tiempo cercano al timeout y no a la demora, y una consulta normal devuelve un estado cacheable con período, fuente y fecha. Cada subclase implementa dos métodos abstractos: uno que arma la fuente normal y otro que la arma contra un backend lento (en el simulado, con una `demora` mayor; en `clientes-09`, con un servidor stub con retardo). El simulado la extiende en este change y el adaptador SOAP la extiende en `clientes-09`.
- **Integración (`@PruebaIntegracion`):** un `@TestConfiguration` reemplaza el bean `fuenteSituacionCrediticia` por una `FuenteDePrueba` que cuenta llamadas y permite forzar caída o demora, delegando en un `SimuladaSituacionAdapter` armado con esa configuración, que respeta el timeout. Cada clase que la usa restablece la fuente y el circuit breaker (`CircuitBreaker.reset()`) en `@BeforeEach`, porque el contexto es compartido.
- `application-test.yml` acorta lo que haría lentos o frágiles los tests: `timeout: 300ms`, y para el circuit breaker `sliding-window-size: 4`, `minimum-number-of-calls: 4` y `wait-duration-in-open-state: 500ms`.
- "Resultado vencido" en integración se prueba moviendo `vigente_hasta` al pasado por SQL; la vigencia con reloj se prueba en el unitario de la caché.

## Risks / Trade-offs

- [Un adaptador nuevo no respeta el timeout y deja el request colgado] → el contrato está en el Javadoc del puerto y lo verifica `ContratoFuenteSituacionTest`, que `clientes-09` tiene que extender (queda anotado en el seed). Además, `calificacion` corta del lado del cliente con su propio timeout.
- [Muchas consultas simultáneas con la fuente lenta ocupan hilos de Tomcat hasta el timeout] → a este volumen no ocurre; si la fuente falla seguido, el circuito se abre y las consultas vuelven al instante. Si la carga creciera, se agrega un `SemaphoreBulkhead` en el decorator de resiliencia.
- [`calificacion` usa un timeout menor que el de `clientes`] → corta antes de recibir `NO_DISPONIBLE` y lo trata como error. Se documenta en el contrato del equipo: su timeout tiene que superar `timeout` + margen (sugerido 3 s).
- [El circuit breaker es por réplica] → con varias réplicas, cada una abre su circuito por separado. Aceptable: la caché sí es compartida (S-01) y una fuente caída termina abriendo todos.
- [`SIN_CONSENTIMIENTO` en el enum sin que nadie lo produzca] → `calificacion` ya lo mapea a 0 puntos; el Javadoc y el contrato aclaran que es reservado. Cuando `clientes-15` agregue la revocación, la rama del servicio y su escenario entran ahí.
- [El caché guarda un resultado que el titular ya no quiere compartir] → hoy solo la anonimización retira el consentimiento, y borra la caché en la misma transacción. `clientes-15` tiene que borrarla también al revocar o reemplazar el CUIL (queda anotado en el seed).
- [Una clave de `calificacion` débil o compartida con `leads`] → validación de largo y de claves distintas al arrancar.

## Migration Plan

1. `V4__situacion_crediticia.sql` crea una tabla nueva; no toca datos existentes. Rollback: revertir el PR y `DROP TABLE situacion_crediticia` (la caché se reconstruye sola).
2. Antes de desplegar, generar `CLIENTES_CLAVE_CALIFICACION` y configurarla en `infra/.env` y en `calificacion`. Sin ella el servicio no arranca, igual que con la clave de `leads`.
3. Avisar a `calificacion` (doc de contrato del equipo) del header, del enum y del timeout antes de que empiece a consultar el endpoint.
