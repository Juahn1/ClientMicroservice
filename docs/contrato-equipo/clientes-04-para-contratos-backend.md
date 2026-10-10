# Para sumar a `contratos-backend.md`, sección "clientes (Juan)"

> Texto listo para pegar, producto del change `clientes-04-cuil-y-consentimientos`. Complementa el texto del change
> 03 (`clientes-03-para-contratos-backend.md`). Los puntos marcados **[Acordar]** necesitan respuesta de quien se
> nombra.

## CUIL y consentimiento en el alta (`leads`)

`leads` reenvía el CUIL y el consentimiento del preformulario en el mismo `POST /clientes`, **solo cuando el
prospecto pidió financiación**:

```json
{
  "nombre": "Martina", "apellido": "López", "email": "martina@gmail.com", "telefono": "11 5555 5555",
  "provincia": "BUENOS_AIRES",
  "cuil": "20-12345678-6",
  "consentimiento": true
}
```

- `cuil` con o sin guiones. `clientes` repite la validación del front: 11 dígitos, prefijo 20, 23, 24 o 27 y dígito
  verificador.
- `consentimiento: true` es el consentimiento de **precalificación crediticia**. `clientes` registra la finalidad,
  la versión del texto aceptado (`v1`), el origen (`PREFORMULARIO`) y la fecha.
- **[Acordar con Iván / `leads`]** La regla "si `solicitaFinanciacion` es true, el CUIL y el consentimiento son
  obligatorios" la valida `leads`: `solicitaFinanciacion` es un dato del Lead y `clientes` no lo conoce. Sin
  financiación, no se mandan ninguno de los dos.
- `leads` no guarda ni loguea el CUIL: lo reenvía y lo descarta.

### Errores nuevos (422, con `errores[].campo` para marcar el formulario)

| `codigo` | Campo | Cuándo |
|---|---|---|
| `CUIL_INVALIDO` | `cuil` | Formato, prefijo o dígito verificador incorrecto |
| `CONSENTIMIENTO_REQUERIDO` | `consentimiento` | Llegó CUIL sin `consentimiento: true` |
| `VALIDACION` | `consentimiento` | Llegó `consentimiento: true` sin CUIL |

### Deduplicación

La respuesta sigue siendo `201` (nuevo) o `200` (ya existía), con `clienteId`. `coincidencia` suma un valor:

| `coincidencia` | Significa |
|---|---|
| `NINGUNA` | Cliente nuevo |
| `CUIL` | El CUIL ya era de un cliente: es esa persona, aunque haya escrito otro email |
| `EMAIL` | El email ya era de un cliente |

Para `leads` no cambia nada: en todos los casos referencia el `clienteId` devuelto. Cuando los datos no cierran
(el email de un cliente con el CUIL de otro, o el mismo email con dos CUIL distintos), `clientes` no pisa nada y
marca al cliente para que lo revise el personal.

## Ficha y edición (front)

`GET /clientes/{id}` suma:

| Campo | Ejemplo | Notas |
|---|---|---|
| `cuil` | `"XX-XXXX5678-X"` | Siempre enmascarado; `null` si el cliente no pidió financiación |
| `consentimientosVigentes` | `[{ "finalidad": "PRECALIFICACION_CREDITICIA", "versionTexto": "v1", "otorgadoEn": "..." }]` | Vacía si no hay |
| `motivoRevision` | `"CUIL_DISTINTO"` / `"POSIBLE_DUPLICADO"` | Junto con `requiereRevision`, que ya existía |

`PATCH /clientes/{id}` (con `If-Match`, igual que en el change 03) suma:

- `telefonoAlternativo`: agrega o reemplaza el teléfono alternativo. No puede ser igual al principal (`422`, campo
  `telefonoAlternativo`).
- `requiereRevision: false`: el telemarketer resolvió la revisión. `true` responde `422`, porque la marca la pone
  solo el alta.

Ninguno de los dos publica `ClienteActualizado`: no son datos que `leads` copie.

## Sobre "no se indexa ni se direcciona por CUIL"

El contrato pide que el CUIL no sea clave ni viaje en rutas, parámetros, eventos o logs. `clientes` lo cumple:

- **Ninguna ruta ni parámetro lleva el CUIL**, no hay búsqueda por CUIL y todo se sigue identificando por
  `clienteId`.
- **Los eventos no lo llevan**, y los logs solo muestran la forma enmascarada.
- En la base el CUIL está **cifrado** (AES-256-GCM). Para detectar que dos altas son la misma persona se guarda
  además un **HMAC** del CUIL (un "índice ciego"): un valor derivado con una clave secreta, que no permite recuperar
  el CUIL y que se usa solo dentro de `clientes` para la deduplicación y la unicidad.

Es un índice interno de unicidad, no una forma de direccionar al cliente: compatible con la regla del contrato.

## Fuera de esta entrega (backlog)

Registrar o corregir el CUIL desde el front, buscar por CUIL, otorgar o revocar consentimientos desde el front y la
finalidad `CONTACTO_COMERCIAL` (preferencias e interesados). El modelo de datos ya los soporta y se suman sin
migrar datos.
