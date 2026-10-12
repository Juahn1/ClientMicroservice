# Para sumar a `contratos-backend.md`, sección "clientes (Juan)"

> Texto listo para pegar, producto del change `clientes-08-historial-calificaciones`. Complementa los textos de los
> changes 03, 04 y 05. Los puntos marcados **[Acordar]** necesitan respuesta de quien se nombra; las **sugerencias**
> no bloquean nada: `clientes` funciona con el evento tal como existe hoy.

## Historial de calificaciones (front)

**Cierra los "[A confirmar]"** del historial: la forma de cada elemento, que `LeadCalificado` lleva `clienteId` y
motivos, y cómo se alimenta (`clientes` consume `lead-calificado`).

```http
GET /clientes/{clienteId}/historial
```

- Lo usa el front en la ficha del lead, con el `clienteId` de la ficha. **Sin `X-Api-Key`**, como la ficha del cliente.
- Devuelve **todas** las calificaciones del cliente (de todos sus leads, automáticas y manuales), **la calculada más
  recientemente primero** y **sin paginar**: un cliente tiene pocas.
- `200` con `calificaciones: []` si todavía no tiene ninguna; `404` (`CLIENTE_NO_ENCONTRADO`) si el ID nunca existió;
  `410` (`CLIENTE_ANONIMIZADO`) si se anonimizó.
- **No es instantáneo:** el historial se alimenta de un evento. Una calificación recién calculada o corregida puede
  tardar unos segundos en aparecer; después de una corrección manual conviene refrescar el historial.

### Respuesta

```json
{
  "clienteId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "calificaciones": [
    {
      "leadId": "550e8400-e29b-41d4-a716-446655440000",
      "nivel": "POTABLE",
      "puntaje": 35,
      "origen": "MANUAL",
      "calculadaEn": "2026-10-11T19:05:00Z",
      "observacion": "Anticipo confirmado por teléfono",
      "motivos": []
    },
    {
      "leadId": "550e8400-e29b-41d4-a716-446655440000",
      "nivel": "MUY_POTABLE",
      "puntaje": 60,
      "origen": "AUTOMATICA",
      "calculadaEn": "2026-10-11T18:30:00Z",
      "observacion": "Calificación automática calculada por motor de reglas",
      "motivos": ["[REGLA_COBERTURA]: Cobertura aportada: 60.00% ...", "..."]
    }
  ]
}
```

| Campo | Tipo | Notas |
|---|---|---|
| `leadId` | UUID | Un cliente puede tener más de un lead |
| `nivel` | texto | Hoy `MUY_POTABLE`, `POTABLE` o `NO_POTABLE`. Es el vocabulario de `calificacion`: el front no debería romperse si aparece otro |
| `puntaje` | entero | Puede ser negativo (−25) |
| `origen` | texto | `AUTOMATICA` o `MANUAL` |
| `calculadaEn` | fecha ISO-8601 en UTC | Cuándo se calculó o corrigió. El front la muestra en hora local |
| `observacion` | texto o `null` | Detalle de la calificación o **motivo de la corrección** manual |
| `motivos` | lista de textos | Desglose de cada regla, en el orden de `calificacion`; puede estar vacía |

- **Desvío del contrato:** no hay usuario de la corrección manual, porque el evento no lo trae. Cuando `calificacion`
  lo envíe, se agrega como un campo más (las calificaciones viejas quedan con `null`).
- La calificación **vigente** de un lead sigue siendo `GET /calificaciones/{leadId}`; el historial no la marca.

## Lo que consume `clientes` de `calificacion`

| | |
|---|---|
| Tópico | `lead-calificado` (lo crea `calificacion`; `clientes` no lo crea) |
| Grupo de consumidores | `clientes-historial`. La primera vez lee el tópico desde el principio |
| Campos obligatorios | `leadId`, `clienteId` (UUID), `puntos` (entero), `nivel`, `origen`, `fecha` |
| Campos opcionales | `observacion`, `motivos` (lista de textos) |
| Campos desconocidos | Se ignoran: agregar campos no rompe nada |
| Formato | El JSON plano de hoy **o** el sobre común (`eventId`, `eventType` = `LeadCalificado`, `version`, `occurredAt`, `correlationId`, `payload`) |
| `fecha` sin zona | Se interpreta en hora de Buenos Aires (configurable con `CLIENTES_HISTORIAL_ZONA_FECHA`) |
| Duplicados | Un mismo `leadId` + `origen` + `fecha` se registra una sola vez: reenviar un evento es seguro |
| Cliente anonimizado o inexistente | El evento se descarta (queda en el log) |
| Mensaje inválido o con falla persistente | Va al tópico `lead-calificado.clientes.DLT` sin frenar a los siguientes; se reprocesa a mano |

- **[Acordar con Manual / `calificacion`]** ¿En qué zona genera la `fecha`? Si el contenedor corre en UTC, `clientes`
  la guardaría corrida 3 horas. Lo más simple es la primera sugerencia de abajo.

### Sugerencias para `LeadCalificado` (no bloquean)

1. **Mandar `fecha` con zona** (`Instant` o `OffsetDateTime`, por ejemplo `"2026-10-11T18:30:00Z"`). Elimina la
   ambigüedad; `clientes` ya acepta los dos formatos.
2. **Usar el sobre común** de integración, como los eventos de `clientes`: aporta `eventId` y `correlationId` para
   seguir un pedido entre servicios. `clientes` ya lo acepta.
3. **Agregar el usuario** que hizo la corrección manual, que el contrato pide para el historial.
4. **Los `motivos` repiten el sufijo de puntos:** `"... (>= 50% (+60 pts)) (+60 pts)"`. `clientes` los guarda y los
   muestra tal cual.

## Dependencia de punta a punta

El historial se prueba completo con eventos de prueba, pero en el flujo real necesita que `leads` pueda dar de alta
clientes: hoy no envía `X-Api-Key`, solo acepta `201` (el recontacto responde `200`) y no envía `provincia`
(obligatoria). Sin el alta no hay `clienteId` en el preformulario ni, por lo tanto, en `LeadCalificado`.

## Variables de entorno

| Variable | Servicio | Para qué |
|---|---|---|
| `CLIENTES_HISTORIAL_ZONA_FECHA` | `clientes` | Zona para la `fecha` de `LeadCalificado` sin zona. Opcional; por defecto `America/Argentina/Buenos_Aires` |
