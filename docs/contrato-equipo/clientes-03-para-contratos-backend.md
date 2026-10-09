# Para sumar a `contratos-backend.md`, sección "clientes (Juan)"

> Texto listo para pegar, producto del change `clientes-03-edicion-y-baja`. Reemplaza la tabla de endpoints de
> `clientes` y agrega dos subsecciones. Los puntos marcados **[Acordar]** necesitan respuesta de quien se nombra.

## Endpoints de `clientes`

| Método | Ruta | Quién lo usa | Respuesta |
|---|---|---|---|
| POST | `/clientes` | `leads` (con `X-Api-Key`) | `201` nuevo o `200` si el email ya existía, con `clienteId` |
| GET | `/clientes/{id}` | Front, `calificacion`, `leads` | Ficha con `ETag`. `410` si el cliente fue anonimizado |
| GET | `/clientes?telefono=&email=&q=&provincia=&page=&size=` | Front | Búsqueda paginada |
| PATCH | `/clientes/{id}` | Front: telemarketer, vendedor o dueño | Edición con `If-Match`; `200` con `ETag` nuevo |
| PUT / DELETE | `/clientes/{id}/no-contactar` | Front | Marca o desmarca "no contactar"; `204` |
| POST | `/clientes/{id}/anonimizacion` | Front | Baja definitiva (Ley 25.326) con el email como confirmación; `204` |
| GET | `/clientes/{id}/historial` | Front | Historial de calificaciones (change propio, pendiente) |

## Edición, no contactar y baja (front)

- **Editar:** mandar en `If-Match` el `ETag` que devolvió `GET /clientes/{id}`. Se editan `nombre`, `apellido`,
  `email`, `telefono`, `provincia` y `localidad`; lo que no se manda no cambia.
  - `412`: otra persona cambió la ficha; hay que recargarla y reintentar.
  - `409 EMAIL_EN_USO`: el email es de otro cliente.
  - `428`: falta `If-Match`.
- **"No me llamen más":** `PUT /clientes/{id}/no-contactar`. No borra nada. La ficha y la búsqueda muestran
  `noContactar`. Se deshace con `DELETE` sobre la misma ruta, o sola si la persona vuelve a llenar el preformulario.
- **"Borren mis datos":** `POST /clientes/{id}/anonimizacion` con `{ "email": "<email del cliente>" }`. Es
  irreversible: la ficha pasa a `410` y el email queda libre. La ejecuta el negocio, no el cliente.
- Header opcional `X-Usuario` (por ejemplo `tm-ana`) en las escrituras, para los logs. Hasta que haya
  autenticación (Integrador), estos endpoints no tienen control de acceso.
- **[Acordar con el gateway]** CORS: exponer `ETag` y permitir los headers `If-Match` y `X-Usuario`.

## Eventos que publica `clientes`

| Tópico | Evento | Payload | Consumidor |
|---|---|---|---|
| `cliente-actualizado` | `ClienteActualizado` | `clienteId`, `campos[]` (`NOMBRE`, `APELLIDO`, `EMAIL`, `TELEFONO`, `PROVINCIA`, `LOCALIDAD`, `NO_CONTACTAR`) | `leads` |
| `cliente-anonimizado` | `ClienteAnonimizado` | `clienteId` | `leads` |

- Sobre común: `eventId`, `eventType`, `version`, `occurredAt`, `correlationId`, `payload`. Clave del mensaje:
  `clienteId`. JSON sin headers de tipo.
- No llevan datos personales; para el valor nuevo, `GET /clientes/{id}`.
- **[Acordar con Iván / `leads`]**
  - Con `NO_CONTACTAR`, consultar la ficha y sacar (o devolver) al cliente de la cola.
  - Con `cliente-anonimizado`, dejar de mostrar los datos copiados del cliente.
  - Tratar el `410` de `GET /clientes/{id}`.
  - Un recontacto que quita la marca no publica evento: el lead nuevo entra a la cola igual.
