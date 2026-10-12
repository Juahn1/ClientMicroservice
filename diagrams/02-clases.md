# Diagrama de clases

El servicio sigue una arquitectura hexagonal: `api → application → domain ← infrastructure`. El dominio define
**puertos** (interfaces) y la infraestructura los implementa. Para que se lea, el diagrama está partido en cuatro
vistas. Se muestran las clases y los métodos principales, no todos los getters.

## 1. Dominio: agregado `Cliente` y value objects

```mermaid
classDiagram
    direction LR

    class Cliente {
        <<Aggregate Root>>
        -UUID id
        -String nombre
        -String apellido
        -Email email
        -Telefono telefono
        -Telefono telefonoAlternativo
        -Provincia provincia
        -String localidad
        -CanalOrigen canalOrigen
        -CanalOrigen ultimoCanal
        -int contactosRegistrados
        -boolean noContactar
        -boolean requiereRevision
        -MotivoRevision motivoRevision
        -CuilProtegido cuil
        -EstadoCliente estado
        -Long version
        +registrarRecontacto(SolicitudAlta, Instant)
        +editar(CambiosCliente, Instant) ResultadoEdicion
        +registrarCuil(CuilProtegido, String, Instant)
        +marcarRevision(MotivoRevision, Instant) boolean
        +resolverRevision(Instant) boolean
        +marcarNoContactar(Instant) boolean
        +quitarNoContactar(Instant) boolean
        +anonimizar(Instant) boolean
        +consentimientosVigentes() List~Consentimiento~
        +activo() boolean
    }

    class Consentimiento {
        -UUID id
        -FinalidadConsentimiento finalidad
        -String versionTexto
        -OrigenConsentimiento origen
        -Instant otorgadoEn
        -Instant revocadoEn
        +vigente() boolean
    }

    class ClienteFactory {
        <<Factory>>
        +solicitud(...) SolicitudAlta
        +cambios(...) CambiosCliente
        +crear(SolicitudAlta) Cliente
    }

    class SolicitudAlta {
        <<record>>
    }
    class CambiosCliente {
        <<record>>
    }
    class ResultadoEdicion {
        <<record>>
        +boolean huboCambios
        +Set~CampoCliente~ camposPublicables
    }
    class ResultadoAlta {
        <<record>>
        +UUID clienteId
        +boolean nuevo
        +Coincidencia coincidencia
    }

    class Email {
        <<Value Object>>
        +de(String)$ Email
    }
    class Telefono {
        <<Value Object>>
        +de(String, String)$ Telefono
    }
    class Cuil {
        <<Value Object>>
        +de(String)$ Cuil
    }
    class CuilProtegido {
        <<Value Object>>
        +byte[] cifrado
        +String hash
        +String ultimos4
    }
    class NombreBusqueda {
        <<Value Object>>
    }

    class EstadoCliente {
        <<enumeration>>
        ACTIVO
        ANONIMIZADO
    }
    class MotivoRevision {
        <<enumeration>>
        CUIL_DISTINTO
        POSIBLE_DUPLICADO
    }
    class Coincidencia {
        <<enumeration>>
        NINGUNA
        EMAIL
        CUIL
    }

    Cliente "1" *-- "0..*" Consentimiento : consentimientos
    Cliente --> Email
    Cliente --> Telefono
    Cliente --> CuilProtegido
    Cliente --> NombreBusqueda
    Cliente --> EstadoCliente
    Cliente --> MotivoRevision
    ClienteFactory ..> Cliente : crea
    ClienteFactory ..> SolicitudAlta : crea
    ClienteFactory ..> CambiosCliente : crea
    ClienteFactory ..> Cuil : valida
    SolicitudAlta --> CuilProtegido
    Cliente ..> ResultadoEdicion : devuelve
    ResultadoAlta --> Coincidencia
```

## 2. Puertos del dominio y sus adaptadores

```mermaid
classDiagram
    direction TB

    class ClienteRepositorio {
        <<interface>>
        +buscarActivoPorEmailParaActualizar(Email) Optional~Cliente~
        +buscarActivoPorCuilParaActualizar(String) Optional~Cliente~
        +buscarPorId(UUID) Optional~Cliente~
        +buscarPorIdParaActualizar(UUID) Optional~Cliente~
        +buscar(FiltroBusqueda, int, int) Pagina~Cliente~
        +guardar(Cliente) Cliente
    }
    class HistorialCalificaciones {
        <<interface>>
        +registrar(CalificacionHistorica, Instant) boolean
        +delCliente(UUID) List~CalificacionHistorica~
        +eliminarDelCliente(UUID)
    }
    class CacheSituacionCrediticia {
        <<interface>>
        +vigente(UUID, Instant) Optional~SituacionCrediticia~
        +guardar(UUID, SituacionCrediticia, Instant)
        +eliminar(UUID)
    }
    class SituacionCrediticiaPort {
        <<interface>>
        +consultar(ConsultaSituacion) SituacionCrediticia
    }
    class CifradorCuil {
        <<interface>>
        +proteger(Cuil) CuilProtegido
        +descifrar(CuilProtegido) Cuil
    }
    class PublicadorEventos {
        <<interface>>
        +publicar(String, UUID, EventoIntegracion)
    }

    class ClienteRepositorioJpa
    class HistorialCalificacionesJpa
    class CacheSituacionCrediticiaJpa
    class AesGcmCifradorCuil
    class PublicadorEventosKafka

    class SituacionCrediticiaConCache {
        <<Decorator>>
    }
    class SituacionCrediticiaResiliente {
        <<Decorator>>
        -CircuitBreaker circuito
    }
    class SimuladaSituacionAdapter {
        <<Adapter>>
    }

    ClienteRepositorio <|.. ClienteRepositorioJpa
    HistorialCalificaciones <|.. HistorialCalificacionesJpa
    CacheSituacionCrediticia <|.. CacheSituacionCrediticiaJpa
    CifradorCuil <|.. AesGcmCifradorCuil
    PublicadorEventos <|.. PublicadorEventosKafka

    SituacionCrediticiaPort <|.. SituacionCrediticiaConCache
    SituacionCrediticiaPort <|.. SituacionCrediticiaResiliente
    SituacionCrediticiaPort <|.. SimuladaSituacionAdapter
    SituacionCrediticiaConCache o-- SituacionCrediticiaResiliente : envuelve
    SituacionCrediticiaResiliente o-- SimuladaSituacionAdapter : envuelve
    SituacionCrediticiaConCache --> CacheSituacionCrediticia

    class SituacionCrediticia {
        <<record>>
        +EstadoSituacionCrediticia estado
        +Integer situacion
        +YearMonth periodo
        +FuenteSituacion fuente
        +Instant consultadaEn
        +esCacheable() boolean
    }
    class CalificacionHistorica {
        <<record>>
        +UUID leadId
        +UUID clienteId
        +String nivel
        +int puntaje
        +String origen
        +Instant calculadaEn
        +String observacion
        +List~String~ motivos
    }
    SituacionCrediticiaPort ..> SituacionCrediticia
    HistorialCalificaciones ..> CalificacionHistorica
```

La cadena del puerto de situación crediticia es **Caché → Resiliencia → Fuente**. La arma `PrecalificacionConfig`
según `clientes.situacion-crediticia.modo`. En la Segunda Parte, `SimuladaSituacionAdapter` se reemplaza por el
adaptador SOAP del BCRA sin tocar los decorators.

## 3. Aplicación: casos de uso y listeners

```mermaid
classDiagram
    direction LR

    class AltaClienteService {
        +registrar(SolicitudAlta) ResultadoAlta
    }
    class ConsultaClienteService {
        +obtener(UUID) Cliente
        +buscar(...) Pagina~Cliente~
    }
    class EdicionClienteService {
        +editar(UUID, long, CambiosCliente) Cliente
    }
    class NoContactarService {
        +marcar(UUID)
        +quitar(UUID)
    }
    class AnonimizacionClienteService {
        +anonimizar(UUID, String)
    }
    class SituacionCrediticiaService {
        +consultar(UUID) SituacionCrediticia
    }
    class RegistroCalificacionService {
        +registrar(CalificacionHistorica) Resultado
    }
    class HistorialCalificacionesService {
        +consultar(UUID) List~CalificacionHistorica~
    }

    class ClienteEventosListener {
        <<Observer AFTER_COMMIT>>
    }
    class SituacionCrediticiaAnonimizacionListener {
        <<Observer BEFORE_COMMIT>>
    }
    class HistorialAnonimizacionListener {
        <<Observer BEFORE_COMMIT>>
    }
    class ClienteActualizadoEvento {
        <<domain event>>
    }
    class ClienteAnonimizadoEvento {
        <<domain event>>
    }

    AltaClienteService --> ClienteRepositorio
    AltaClienteService --> ClienteFactory
    EdicionClienteService --> ClienteRepositorio
    EdicionClienteService ..> ClienteActualizadoEvento : publica
    NoContactarService ..> ClienteActualizadoEvento : publica
    AnonimizacionClienteService --> ClienteRepositorio
    AnonimizacionClienteService ..> ClienteAnonimizadoEvento : publica
    SituacionCrediticiaService --> ConsultaClienteService
    SituacionCrediticiaService --> CifradorCuil
    SituacionCrediticiaService --> SituacionCrediticiaPort
    RegistroCalificacionService --> HistorialCalificaciones
    RegistroCalificacionService --> ClienteRepositorio
    HistorialCalificacionesService --> ConsultaClienteService
    HistorialCalificacionesService --> HistorialCalificaciones

    ClienteEventosListener ..> ClienteActualizadoEvento : escucha
    ClienteEventosListener ..> ClienteAnonimizadoEvento : escucha
    ClienteEventosListener --> PublicadorEventos
    SituacionCrediticiaAnonimizacionListener ..> ClienteAnonimizadoEvento : escucha
    SituacionCrediticiaAnonimizacionListener --> CacheSituacionCrediticia
    HistorialAnonimizacionListener ..> ClienteAnonimizadoEvento : escucha
    HistorialAnonimizacionListener --> HistorialCalificaciones
```

## 4. Entradas: API REST y consumidor de Kafka

```mermaid
classDiagram
    direction LR

    class ClienteController {
        <<RestController /clientes>>
        +alta(AltaClienteRequest) AltaClienteResponse
        +buscar(...) PaginaResponse
        +obtener(UUID) ClienteResponse
        +editar(UUID, EdicionClienteRequest) ClienteResponse
        +marcarNoContactar(UUID)
        +quitarNoContactar(UUID)
        +anonimizar(UUID, AnonimizacionRequest)
    }
    class SituacionCrediticiaController {
        <<RestController>>
        +consultar(UUID) SituacionCrediticiaResponse
    }
    class HistorialController {
        <<RestController>>
        +consultar(UUID) HistorialResponse
    }
    class ApiExceptionHandler {
        <<ControllerAdvice>>
    }
    class ClaveServicioInterceptor {
        <<Interceptor>>
    }
    class IfMatchInterceptor {
        <<Interceptor>>
    }

    class LeadCalificadoListener {
        <<KafkaListener lead-calificado>>
        +recibir(ConsumerRecord)
    }
    class LeadCalificadoTraductor {
        <<Translator>>
        +traducir(byte[]) Traducido
    }
    class ConsumidorKafkaConfig {
        <<Configuration>>
        +manejadorErroresHistorial() DefaultErrorHandler
    }
    class MensajeInvalidoException

    ClienteController --> AltaClienteService
    ClienteController --> ConsultaClienteService
    ClienteController --> EdicionClienteService
    ClienteController --> NoContactarService
    ClienteController --> AnonimizacionClienteService
    SituacionCrediticiaController --> SituacionCrediticiaService
    HistorialController --> HistorialCalificacionesService
    ApiExceptionHandler ..> ClienteController : traduce errores a ProblemDetail
    ClaveServicioInterceptor ..> ClienteController : X-Api-Key en el alta
    ClaveServicioInterceptor ..> SituacionCrediticiaController : X-Api-Key
    IfMatchInterceptor ..> ClienteController : If-Match en PATCH

    LeadCalificadoListener --> LeadCalificadoTraductor
    LeadCalificadoListener --> RegistroCalificacionService
    LeadCalificadoTraductor ..> MensajeInvalidoException : lanza
    ConsumidorKafkaConfig ..> MensajeInvalidoException : no reintentable, al DLT
```
