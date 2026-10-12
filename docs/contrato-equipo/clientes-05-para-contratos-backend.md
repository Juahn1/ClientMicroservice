# Para sumar a `contratos-backend.md`, sección "clientes (Juan)"

> Texto listo para pegar, producto del change `clientes-05-situacion-crediticia`. Complementa los textos de los
> changes 03 y 04. Los puntos marcados **[Acordar]** necesitan respuesta de quien se nombra.

## Situación crediticia (`calificacion`)

**Cierra el "[A confirmar]"** de si la situación crediticia sigue en `clientes`: **sí, sigue acá** como dato derivado y
cacheado. Para consultarla hay que descifrar el CUIL, y el CUIL no sale de este servicio.

```http
GET /clientes/{clienteId}/situacion-crediticia
X-Api-Key: <CLIENTES_CLAVE_CALIFICACION>
```

- **Solo para `calificacion`.** Exige `X-Api-Key` con una clave propia de `calificacion`, distinta de la de `leads`
  (la de `leads` no abre este endpoint y la de `calificacion` no abre el alta). Sin clave o con otra: `401` con
  `codigo` = `CLAVE_SERVICIO_INVALIDA`. La clave se configura igual en los dos servicios (variable
  `CLIENTES_CLAVE_CALIFICACION`, mínimo 32 caracteres). El front no usa este endpoint.
- **Siempre `200`** para un cliente activo, aunque la fuente esté caída o lenta. Nunca `5xx` por culpa de la fuente.
- `404` (`CLIENTE_NO_ENCONTRADO`) si el ID nunca existió; `410` (`CLIENTE_ANONIMIZADO`) si se anonimizó.
- **La respuesta nunca incluye el CUIL**, ni enmascarado.

### Respuesta

La forma es siempre la misma; los campos que no aplican van en `null`.

| Campo | Tipo | Cuándo tiene valor |
|---|---|---|
| `clienteId` | UUID | Siempre |
| `estado` | enum `EstadoSituacionCrediticia` | Siempre |
| `situacion` | entero de 1 a 6 | Solo con `CON_DATOS` |
| `periodo` | texto `AAAAMM` | Con `CON_DATOS` o `SIN_REGISTROS` |
| `fuente` | `SIMULADA` (Primera Parte) / `BCRA` (Segunda) | Con `CON_DATOS` o `SIN_REGISTROS` |
| `consultadaEn` | fecha ISO-8601 | Con `CON_DATOS` o `SIN_REGISTROS`; puede ser anterior al request si viene de la caché |

```json
{ "clienteId": "a1b2...", "estado": "CON_DATOS", "situacion": 1, "periodo": "202609",
  "fuente": "SIMULADA", "consultadaEn": "2026-10-10T13:02:45.123456Z" }
```

```json
{ "clienteId": "a1b2...", "estado": "SIN_CUIL", "situacion": null, "periodo": null,
  "fuente": null, "consultadaEn": null }
```

### Estados

El enum `EstadoSituacionCrediticia` está en el módulo `contracts` (`com.uade.crm.contracts.clientes`).

| `estado` | Significa | Puntos sugeridos en `ReglaFinanciacion` (AC-05) |
|---|---|---|
| `CON_DATOS` | La fuente informó una situación | 1 o 2 → **+20**; 3 a 6 → **−25** |
| `SIN_REGISTROS` | La fuente no tiene registros de ese CUIL | 0 |
| `SIN_CUIL` | El cliente no pidió financiación (no hay CUIL); no se consulta la fuente | 0 |
| `SIN_CONSENTIMIENTO` | **Reservado.** Hoy no se produce: un cliente con CUIL siempre tiene el consentimiento. Aparece cuando el titular pueda revocarlo (backlog `clientes-15`) | 0 |
| `NO_DISPONIBLE` | La fuente falló, no respondió a tiempo o está fuera de servicio. Conviene reintentar más tarde | 0 |

- **[Acordar con Manual / `calificacion`]** El mapeo de puntos de la tabla y que `calificacion` trate ya los cinco
  estados, incluido `SIN_CONSENTIMIENTO`, para no tener que tocarlo cuando aparezca.
- **[Acordar con Manual / `calificacion`]** Si conviene recalificar más tarde un lead calificado con `NO_DISPONIBLE`.
  `clientes` no guarda ese resultado, así que la próxima consulta vuelve a intentar la fuente.

### Tiempos y caché

- `clientes` corta la consulta a la fuente a los **2 s** y responde `NO_DISPONIBLE`. **El cliente REST de
  `calificacion` tiene que esperar más** (sugerido: 3 s); si corta antes, recibe un error en lugar del estado.
- Los resultados `CON_DATOS` y `SIN_REGISTROS` se guardan **30 días** por cliente. Dentro de ese plazo la respuesta
  sale de la caché, al instante y sin consultar la fuente.
- Si la fuente falla seguido, `clientes` deja de consultarla un rato (circuit breaker de 30 s) y responde
  `NO_DISPONIBLE` de inmediato.
- Al anonimizar un cliente se borra su situación crediticia guardada.

## Variables de entorno

| Variable | Servicio | Para qué |
|---|---|---|
| `CLIENTES_CLAVE_CALIFICACION` | `clientes` y `calificacion` | Clave que `calificacion` manda en `X-Api-Key`. Obligatoria: sin ella `clientes` no arranca |
