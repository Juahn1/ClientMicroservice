# Diagramas de secuencia

Los flujos principales del servicio, de la entrada (REST o Kafka) a la base y los eventos.

1. [Alta de cliente con deduplicación](#1-alta-de-cliente-con-deduplicación)
2. [Edición con control de concurrencia](#2-edición-con-control-de-concurrencia)
3. [Anonimización](#3-anonimización)
4. [Consulta de la situación crediticia](#4-consulta-de-la-situación-crediticia)
5. [Registro de una calificación (consumo de `lead-calificado`)](#5-registro-de-una-calificación-consumo-de-lead-calificado)
6. [Consulta del historial de calificaciones](#6-consulta-del-historial-de-calificaciones)

## 1. Alta de cliente con deduplicación

`leads` da de alta al cliente cuando se completa el preformulario. Si la persona ya existía (por CUIL o por email),
no se crea otra ficha.

```mermaid
sequenceDiagram
    autonumber
    participant L as leads
    participant I as ClaveServicioInterceptor
    participant C as ClienteController
    participant F as ClienteFactory
    participant S as AltaClienteService
    participant R as ClienteRepositorio
    participant DB as PostgreSQL

    L->>I: POST /clientes + X-Api-Key
    alt clave ausente o de otro servicio
        I-->>L: 401 CLAVE_SERVICIO_INVALIDA
    end
    I->>C: request válido
    C->>F: solicitud(datos)
    Note over F: normaliza email y teléfono (E.164 móvil),<br/>valida y cifra el CUIL (AES-GCM + HMAC)
    alt dato inválido
        F-->>C: TelefonoInvalido / CuilInvalido / ConsentimientoRequerido
        C-->>L: 422 ProblemDetail
    end
    F-->>C: SolicitudAlta
    C->>S: registrar(solicitud)
    S->>R: buscarActivoPorCuilParaActualizar(cuilHash)
    R->>DB: SELECT ... FOR UPDATE
    alt coincide por CUIL
        S->>S: registrarRecontacto() y, si el email es de otro cliente, marcarRevision(POSIBLE_DUPLICADO)
        S->>R: guardar(existente)
        S-->>C: ResultadoAlta(id, nuevo=false, CUIL)
    else sin coincidencia por CUIL
        S->>R: buscarActivoPorEmailParaActualizar(email)
        R->>DB: SELECT ... FOR UPDATE
        alt coincide por email
            S->>S: registrarRecontacto() y registrarCuil() o marcarRevision(CUIL_DISTINTO)
            S->>R: guardar(existente)
            S-->>C: ResultadoAlta(id, nuevo=false, EMAIL)
        else cliente nuevo
            S->>F: crear(solicitud)
            S->>R: guardar(nuevo)
            R->>DB: INSERT cliente (+ consentimiento)
            S-->>C: ResultadoAlta(id, nuevo=true, NINGUNA)
        end
    end
    Note over S,DB: si un alta simultánea gana la carrera (índice único),<br/>se reintenta una vez y termina como recontacto
    C-->>L: 201 Created / 200 OK + Location + { clienteId, nuevo, coincidencia }
```

## 2. Edición con control de concurrencia

```mermaid
sequenceDiagram
    autonumber
    actor P as Personal (front)
    participant C as ClienteController
    participant S as EdicionClienteService
    participant Cl as Cliente
    participant R as ClienteRepositorio
    participant Ev as ClienteEventosListener
    participant K as Kafka

    P->>C: GET /clientes/{id}
    C-->>P: 200 ficha + ETag W/"3"
    P->>C: PATCH /clientes/{id} + If-Match W/"3"
    alt falta If-Match
        C-->>P: 428 PRECONDICION_REQUERIDA
    end
    C->>S: editar(id, 3, cambios)
    S->>R: buscarPorId(id)
    alt versión distinta de 3
        S-->>C: VersionDesactualizadaException
        C-->>P: 412 VERSION_DESACTUALIZADA
    end
    S->>R: ¿el email nuevo es de otro cliente?
    alt sí
        C-->>P: 409 EMAIL_EN_USO
    end
    S->>Cl: editar(cambios)
    Cl-->>S: ResultadoEdicion(huboCambios, camposPublicables)
    alt hubo cambios
        S->>R: guardar(cliente) (versión 4)
        S-)Ev: ClienteActualizadoEvento (si hay campos publicables)
        Note over Ev: AFTER_COMMIT
        Ev-)K: cliente-actualizado { clienteId, campos }
    end
    C-->>P: 200 ficha + ETag W/"4"
```

## 3. Anonimización

Todo ocurre en una transacción. Los módulos que guardan datos del cliente fuera del agregado reaccionan al evento de
dominio antes del commit.

```mermaid
sequenceDiagram
    autonumber
    actor P as Personal (front)
    participant C as ClienteController
    participant S as AnonimizacionClienteService
    participant R as ClienteRepositorio
    participant Cl as Cliente
    participant LS as SituacionCrediticiaAnonimizacionListener
    participant LH as HistorialAnonimizacionListener
    participant LE as ClienteEventosListener
    participant DB as PostgreSQL
    participant K as Kafka

    P->>C: POST /clientes/{id}/anonimizacion { email }
    C->>S: anonimizar(id, email)
    S->>R: buscarPorIdParaActualizar(id)
    R->>DB: SELECT ... FOR UPDATE
    alt no existe
        C-->>P: 404 CLIENTE_NO_ENCONTRADO
    else ya anonimizado
        C-->>P: 204 (idempotente)
    else email no coincide
        C-->>P: 422 CONFIRMACION_INVALIDA
    end
    S->>Cl: anonimizar(ahora)
    Note over Cl: nombre y apellido = ANONIMIZADO<br/>email, teléfonos, localidad y CUIL = null<br/>consentimientos revocados
    S->>R: guardar(cliente)
    S-)LS: ClienteAnonimizadoEvento
    S-)LH: ClienteAnonimizadoEvento
    Note over LS,LH: BEFORE_COMMIT (misma transacción)
    LS->>DB: DELETE situacion_crediticia
    LH->>DB: DELETE historial_calificacion
    Note over S,DB: COMMIT (si un borrado falla, se revierte todo)
    S-)LE: ClienteAnonimizadoEvento
    Note over LE: AFTER_COMMIT
    LE-)K: cliente-anonimizado { clienteId }
    C-->>P: 204 No Content
```

## 4. Consulta de la situación crediticia

```mermaid
sequenceDiagram
    autonumber
    participant Q as calificacion
    participant C as SituacionCrediticiaController
    participant S as SituacionCrediticiaService
    participant Ci as CifradorCuil
    participant Ca as SituacionCrediticiaConCache
    participant Re as SituacionCrediticiaResiliente
    participant Fu as SimuladaSituacionAdapter
    participant DB as PostgreSQL

    Q->>C: GET /clientes/{id}/situacion-crediticia + X-Api-Key
    C->>S: consultar(id)
    alt inexistente o anonimizado
        C-->>Q: 404 / 410
    else cliente sin CUIL
        S-->>C: SIN_CUIL (no consulta la fuente)
    else cliente con CUIL
        S->>Ci: descifrar(cuilProtegido)
        Ci-->>S: Cuil (solo en memoria)
        S->>Ca: consultar(clienteId, cuil)
        Ca->>DB: ¿resultado vigente?
        alt hay resultado en caché (menos de 30 días)
            Ca-->>S: CON_DATOS / SIN_REGISTROS guardado
        else sin caché o vencido
            Ca->>Re: consultar
            alt circuito abierto
                Re-->>Ca: NO_DISPONIBLE (sin llamar a la fuente)
            else circuito cerrado o semiabierto
                Re->>Fu: consultar (como mucho 2 s)
                alt la fuente responde
                    Fu-->>Re: CON_DATOS / SIN_REGISTROS
                    Re-->>Ca: resultado
                    Ca->>DB: INSERT ... SELECT ... FOR SHARE ON CONFLICT DO UPDATE
                else falla o timeout
                    Fu-->>Re: FuenteNoDisponibleException
                    Re-->>Ca: NO_DISPONIBLE (no se guarda)
                end
            end
            Ca-->>S: resultado
        end
    end
    C-->>Q: 200 { estado, situacion, periodo, fuente, consultadaEn }
```

## 5. Registro de una calificación (consumo de `lead-calificado`)

```mermaid
sequenceDiagram
    autonumber
    participant Q as calificacion
    participant K as Kafka
    participant L as LeadCalificadoListener
    participant T as LeadCalificadoTraductor
    participant S as RegistroCalificacionService
    participant H as HistorialCalificaciones
    participant R as ClienteRepositorio
    participant DB as PostgreSQL
    participant E as DefaultErrorHandler

    Q-)K: LeadCalificado (clave leadId)
    K->>L: ConsumerRecord<String, byte[]>
    L->>T: traducir(bytes)
    alt JSON roto o campo inválido
        T-->>L: MensajeInvalidoException
        L-->>E: excepción
        E-)K: lead-calificado.clientes.DLT (sin reintentos) + WARN
    else válido
        T-->>L: CalificacionHistorica (fecha sin zona -> Buenos Aires)
        L->>S: registrar(calificacion)
        S->>H: registrar(calificacion, ahora)
        H->>DB: INSERT ... SELECT ... WHERE cliente ACTIVO FOR SHARE<br/>ON CONFLICT (lead_id, origen, calculada_en) DO NOTHING
        alt 1 fila
            S-->>L: REGISTRADA (log INFO)
        else 0 filas
            S->>R: buscarPorId(clienteId) (solo para el log)
            S-->>L: DUPLICADA (DEBUG) / CLIENTE_ANONIMIZADO (INFO) / CLIENTE_INEXISTENTE (WARN)
        end
        opt falla transitoria (por ejemplo, la base caída)
            S-->>E: excepción
            E->>L: reintenta tras 2s, 4s, 8s, 16s, 32s
            E-)K: si sigue fallando: lead-calificado.clientes.DLT + WARN
        end
    end
    Note over L,K: commit del offset y sigue con el próximo mensaje
```

## 6. Consulta del historial de calificaciones

```mermaid
sequenceDiagram
    autonumber
    actor P as Front (ficha del lead)
    participant C as HistorialController
    participant S as HistorialCalificacionesService
    participant Co as ConsultaClienteService
    participant H as HistorialCalificaciones
    participant DB as PostgreSQL

    P->>C: GET /clientes/{id}/historial
    C->>S: consultar(id)
    S->>Co: obtener(id)
    alt inexistente
        C-->>P: 404 CLIENTE_NO_ENCONTRADO
    else anonimizado
        C-->>P: 410 CLIENTE_ANONIMIZADO
    end
    S->>H: delCliente(id)
    H->>DB: SELECT ... ORDER BY calculada_en DESC
    H-->>S: List<CalificacionHistorica>
    C-->>P: 200 { clienteId, calificaciones: [...] }
```
