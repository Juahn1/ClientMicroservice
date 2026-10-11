# Diagramas de estados

1. [Cliente](#1-cliente)
2. [Consentimiento](#2-consentimiento)
3. [Situación crediticia (caché y circuit breaker)](#3-situación-crediticia-caché-y-circuit-breaker)
4. [Mensaje de `lead-calificado`](#4-mensaje-de-lead-calificado)

## 1. Cliente

El estado persistido es `ACTIVO` o `ANONIMIZADO`. Mientras está activo, el cliente tiene además dos marcas
independientes: "no contactar" y "requiere revisión". La anonimización es **irreversible**: un cliente anonimizado no
admite ninguna otra operación (`410 CLIENTE_ANONIMIZADO`).

```mermaid
stateDiagram-v2
    [*] --> Activo : POST /clientes (alta nueva, 201)

    state Activo {
        direction LR
        state "Contacto" as Contacto {
            [*] --> Contactable
            Contactable --> NoContactar : PUT /no-contactar
            NoContactar --> Contactable : DELETE /no-contactar
            NoContactar --> Contactable : recontacto (POST /clientes)
        }
        --
        state "Revisión" as Revision {
            [*] --> SinRevision
            SinRevision --> RequiereRevision : alta con CUIL_DISTINTO o POSIBLE_DUPLICADO
            RequiereRevision --> RequiereRevision : otra coincidencia (conserva el primer motivo)
            RequiereRevision --> SinRevision : PATCH requiereRevision=false
        }
    }

    Activo --> Activo : PATCH (edición) / recontacto / registrar CUIL
    Activo --> Anonimizado : POST /anonimizacion (email correcto)
    Anonimizado --> Anonimizado : POST /anonimizacion (idempotente)
    Anonimizado --> [*]

    note right of Anonimizado
        sin nombre, email, teléfonos, localidad ni CUIL
        consentimientos revocados
        situación crediticia e historial borrados
        email y CUIL libres para un alta nueva
    end note
```

## 2. Consentimiento

Un cliente con CUIL siempre tiene vigente el consentimiento de `PRECALIFICACION_CREDITICIA`; los dos llegan juntos en
el alta. Hoy solo la anonimización lo revoca. La revocación a pedido del titular está en el backlog (`clientes-15`).

```mermaid
stateDiagram-v2
    [*] --> Vigente : alta con CUIL + consentimiento=true<br/>(o recontacto que registra el CUIL)
    Vigente --> Revocado : anonimización del cliente
    Vigente --> Revocado : revocación del titular (backlog clientes-15)
    Revocado --> [*]

    note right of Revocado
        la fila se conserva con revocado_en
        (prueba de que hubo consentimiento)
    end note
```

## 3. Situación crediticia (caché y circuit breaker)

### 3.1 Resultado guardado por cliente

```mermaid
stateDiagram-v2
    [*] --> SinCache
    SinCache --> Vigente : la fuente responde CON_DATOS o SIN_REGISTROS
    SinCache --> SinCache : NO_DISPONIBLE (no se guarda)
    Vigente --> Vigente : consulta dentro de los 30 días (no llama a la fuente)
    Vigente --> Vencido : pasan 30 días (vigente_hasta)
    Vencido --> Vigente : la fuente responde (se reemplaza)
    Vencido --> Vencido : NO_DISPONIBLE (se conserva el vencido, no se usa)
    Vigente --> [*] : anonimización (borrado en la misma transacción)
    Vencido --> [*] : anonimización
```

### 3.2 Circuit breaker de la fuente (Resilience4j, instancia `situacionCrediticia`)

```mermaid
stateDiagram-v2
    [*] --> Cerrado
    Cerrado --> Cerrado : llamada a la fuente (éxito o falla aislada)
    Cerrado --> Abierto : 50 % o más de fallas en las últimas 10 llamadas
    Abierto --> Abierto : consulta -> NO_DISPONIBLE al instante, sin llamar a la fuente
    Abierto --> SemiAbierto : pasan 30 s
    SemiAbierto --> Cerrado : la llamada de prueba funciona
    SemiAbierto --> Abierto : la llamada de prueba falla
```

## 4. Mensaje de `lead-calificado`

Ciclo de vida de cada mensaje que consume el historial de calificaciones. En todos los estados finales se confirma el
offset y el consumidor sigue con el mensaje siguiente.

```mermaid
stateDiagram-v2
    [*] --> Recibido
    Recibido --> Invalido : no es JSON o falta/está mal un campo
    Recibido --> Traducido : formato válido (plano o sobre común)

    Traducido --> Registrado : INSERT de 1 fila
    Traducido --> Descartado : INSERT de 0 filas
    Traducido --> Reintentando : falla transitoria (base caída, timeout)

    Reintentando --> Registrado : un reintento funciona
    Reintentando --> Descartado : un reintento inserta 0 filas
    Reintentando --> EnviadoAlDLT : se agotan los 5 reintentos (unos 62 s)
    Invalido --> EnviadoAlDLT : sin reintentos

    Registrado --> [*]
    Descartado --> [*]
    EnviadoAlDLT --> [*]

    note right of Descartado
        duplicado (DEBUG)
        cliente anonimizado (INFO)
        cliente inexistente (WARN)
    end note
    note right of EnviadoAlDLT
        lead-calificado.clientes.DLT con el contenido original
        WARN con partición, offset y clave
        reproceso manual: republicar en lead-calificado
    end note
```
