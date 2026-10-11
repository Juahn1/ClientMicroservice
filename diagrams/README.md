# Diagramas del servicio `clientes`

| Diagrama | Qué muestra |
| --- | --- |
| [Entidad-relación](01-entidad-relacion.md) | Tablas de `clientes_db` (`cliente`, `consentimiento`, `situacion_crediticia`, `historial_calificacion`), sus relaciones, restricciones e índices |
| [Clases](02-clases.md) | Agregado `Cliente` y value objects, puertos del dominio con sus adaptadores (incluidos los decorators de la situación crediticia), casos de uso y listeners, y entradas REST y Kafka |
| [Secuencia](03-secuencia.md) | Alta con deduplicación, edición con `ETag`/`If-Match`, anonimización, situación crediticia, consumo de `lead-calificado` y consulta del historial |
| [Estados](04-estados.md) | Cliente (activo, no contactar, revisión, anonimizado), consentimiento, caché y circuit breaker de la situación crediticia, y cada mensaje de `lead-calificado` |

Los diagramas están escritos en [Mermaid](https://mermaid.js.org/) dentro de Markdown:

- **GitHub** los muestra dibujados al abrir cada archivo.
- **IntelliJ y VS Code** los muestran con el plugin o la extensión de Mermaid.
- Para exportarlos como imagen, pegar el bloque en [mermaid.live](https://mermaid.live).

Reflejan el código de la rama `dev` hasta el change `clientes-08-historial-calificaciones`. Si un change modifica el
modelo, los flujos o los estados, conviene actualizar el diagrama en el mismo PR. La documentación general del
servicio está en el [README](../README.md).
