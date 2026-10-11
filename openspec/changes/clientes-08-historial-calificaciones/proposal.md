# Proposal: Historial de calificaciones

## Why

El contrato del equipo (`contratos-backend.md` v1.1) movió el historial de calificaciones de `calificacion` a `clientes`: el front lo muestra en la ficha del lead con `GET /clientes/{id}/historial`, y un mismo cliente puede tener varios leads. `calificacion` ya publica `LeadCalificado` en cada cálculo y en cada corrección manual, y su responsable confirmó el 2026-10-11 que el evento lleva el `clienteId`. Este change hace que `clientes` consuma ese evento y lo exponga. Es el primer consumidor de Kafka del servicio. El nombre `clientes-08-historial-calificaciones` es el que anunció el change 02.

## What Changes

- **Consumidor de `lead-calificado`** (grupo `clientes-historial`, desde el principio del tópico la primera vez). Acepta el evento tal como lo publica hoy `calificacion`: JSON plano sin el sobre común, con `fecha` sin zona. También acepta el sobre común, si `calificacion` lo adopta más adelante.
  - Una `fecha` sin zona se interpreta en una zona configurable, por defecto `America/Argentina/Buenos_Aires`. Si trae offset, se respeta.
  - **Idempotente por clave natural** (`leadId`, `origen`, fecha de cálculo): un mensaje repetido no duplica el historial.
  - **Solo registra calificaciones de clientes activos.** Si el cliente está anonimizado o no existe, el evento se descarta con un log cuyo nivel depende del motivo. Si el evento ya estaba registrado, también se descarta.
  - **Mensajes que no se pueden procesar.** Un mensaje ilegible o inválido va directo al tópico de mensajes fallidos `lead-calificado.clientes.DLT`. Una falla transitoria, como la base caída, se reintenta con espera exponencial acotada y, si persiste, también termina en ese tópico. En ningún caso se frena la lectura del resto.
  - Los logs del consumidor no incluyen `motivos` ni `observacion`, que describen la situación financiera de la persona.
- **`GET /clientes/{id}/historial`**: devuelve todas las calificaciones del cliente, la más reciente primero y sin paginar. Usa los nombres del contrato del equipo (`leadId`, `nivel`, `puntaje`, `origen`, `calculadaEn`, `observacion`, `motivos`). No exige clave de servicio. Cliente sin calificaciones → 200 con lista vacía; inexistente → 404; anonimizado → 410.
- **Anonimización:** también elimina el historial del cliente, en la misma operación.
- Migración `V5__historial_calificacion.sql`.
- **Desvíos respecto del contrato del equipo:**
  - no se incluye el usuario de la corrección manual, porque el evento no lo trae; se agrega cuando `calificacion` lo envíe;
  - el motivo de la corrección viaja en `observacion`, presente en todas las calificaciones.
- **Fuera de alcance:**
  - reprocesar los mensajes del tópico de fallidos (es manual);
  - filtro por lead y marca de calificación vigente (la vigente la da `GET /calificaciones/{leadId}`);
  - paginación (se puede sumar sin romper la respuesta);
  - tabla de `eventId` procesados (la idempotencia sale de la clave natural);
  - reactivación por stock, que pasa a llamarse `clientes-16` en el backlog del seed.

## Capabilities

### New Capabilities

- `clientes-historial-calificaciones`: registro de las calificaciones que publica `calificacion` (consumo idempotente, interpretación de la fecha, descarte de clientes no activos, mensajes fallidos y reintentos) y consulta del historial de un cliente.

### Modified Capabilities

- `clientes-gestion`: la anonimización también elimina el historial de calificaciones del cliente.

## Impact

- **Código (`clientes-service`):**
  - dominio: `CalificacionHistorica` y el puerto `HistorialCalificaciones`;
  - aplicación: `RegistroCalificacionService`, `HistorialCalificacionesService` y el listener que borra el historial al anonimizar;
  - infraestructura: el consumidor de Kafka y la traducción del mensaje en `infrastructure/messaging`, la configuración del consumidor, de los reintentos y del tópico de fallidos, y la entidad y el repositorio del historial;
  - API: controller y DTOs de `GET /clientes/{id}/historial`.
- **Configuración:**
  - `spring.kafka.consumer.*` (grupo, offset inicial, sin crear tópicos ajenos);
  - `clientes.kafka.topicos.lead-calificado` y `lead-calificado-dlt`;
  - `clientes.historial.*` (zona de la fecha y reintentos), con valores cortos en el perfil de test.
- **Dependencias:** Awaitility para los tests del consumidor. Es una dependencia de test, con versión gestionada por Spring Boot.
- **`calificacion` (coordinar con su responsable, sin bloquear el change):** sugerencias para `LeadCalificado` en el documento del equipo:
  - enviar la `fecha` con zona;
  - adoptar el sobre común;
  - sumar el usuario de las correcciones;
  - corregir el sufijo de puntos duplicado en `motivos`.
- **Front:** forma de `GET /clientes/{id}/historial`, en `docs/contrato-equipo/clientes-08-para-contratos-backend.md`.
- **Dependencia de punta a punta:** el flujo real necesita que `leads` pueda dar de alta clientes (hoy no envía `X-Api-Key`, solo acepta 201 y no envía `provincia`). El change se prueba completo con eventos publicados por los tests.
- **Seed:** el change 08 se suma a la tabla de §6 y la reactivación por stock pasa a `clientes-16` en §8.
