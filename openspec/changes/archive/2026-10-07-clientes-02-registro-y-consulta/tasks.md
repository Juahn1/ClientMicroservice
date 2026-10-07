# Tasks

## 1. Módulo contracts y configuración

- [x] 1.1 Crear el módulo Maven `contracts` (sin Spring) con `Provincia` (24 jurisdicciones) y `CanalOrigen` en `com.uade.crm.contracts` (design D10), registrarlo en el POM padre y como dependencia de `clientes-service`; verificar que `mvn -q verify` compila y que un test unitario confirma los 24 valores de `Provincia`
- [x] 1.2 Agregar `clientes.seguridad.clave-leads` (mínimo 32 caracteres, `toString` enmascarado) y `clientes.paginacion.tamanio-por-defecto` a `ClientesProperties`, `application.yml`, `application-test.yml`, `infra/.env.example` y docker-compose (`CLIENTES_CLAVE_LEADS`); verificar con un test, como `ClavesCuilTest`, que el arranque falla sin la clave o con una clave corta
- [x] 1.3 Agregar `TELEFONO_INVALIDO` (422) y `CLAVE_SERVICIO_INVALIDA` (401) a `CodigoError`, y hacer que un valor fuera de un enum en el JSON responda 422 `VALIDACION` con el campo, no 400 (design D9); verificarlo ampliando `ErroresApiTest`

## 2. Persistencia

- [x] 2.1 Escribir `V1__cliente.sql` con la tabla, el CHECK y los cuatro índices del design D3; verificar que `ContextoTest` (Testcontainers, `ddl-auto: validate`) arranca con la migración aplicada
- [x] 2.2 Implementar `ClienteEntity` (`@Version`), `ClienteJpaRepository` y el adaptador del puerto `ClienteRepositorio` (buscar activo por email, por id, guardar); verificar con un test de repositorio sobre Testcontainers que un segundo cliente activo con el mismo email lanza `DataIntegrityViolationException`

## 3. Dominio

- [x] 3.1 Value object `Telefono` (libphonenumber + inserción del 9, ausente si viene en blanco) y enmascarado mediante `Enmascarador`; verificar con tests unitarios sobre la tabla de §5.6 del seed
- [x] 3.2 Value objects `Email` (trim + minúsculas) y `NombreBusqueda` (NFD, sin marcas, minúsculas, espacios colapsados); verificar con tests unitarios: `" Martina@Gmail.com "`, `"María José" + "PEÑA"` → `"maria jose pena"`
- [x] 3.3 Modelo `Cliente` con `registrarRecontacto(...)` según el design D4, paso 4 (no pisa nombre, apellido ni provincia; completa localidad, canal y teléfono o alternativo; suma contacto); verificar con tests unitarios que cubren el escenario "Recontacto con datos distintos" y "Recontacto que completa el teléfono"
- [x] 3.4 `ClienteFactory` para clientes nuevos (contactos = 1, `ultimoCanal = canalOrigen`, `nombreBusqueda`, fechas); verificar con tests unitarios, incluido el caso sin teléfono ni canal

## 4. Acceso de servicios

- [x] 4.1 Implementar `@RequiereClaveServicio` + `ClaveServicioInterceptor` (comparación en tiempo constante, 401 vía `ApiExceptionHandler`, `WWW-Authenticate`) según el design D2; verificar con un test web que cubra todos los escenarios de "Acceso restringido a servicios autorizados", incluido el de logs con `OutputCaptureExtension`

## 5. Alta

- [x] 5.1 `AltaClienteService` con el algoritmo del design D4 (transacción por intento y un reintento ante `DataIntegrityViolationException`); verificar con tests de integración (Testcontainers) los escenarios "Cliente nuevo", "Mismo email con otro formato" y "Altas simultáneas con el mismo email"
- [x] 5.2 `POST /clientes` con `AltaClienteRequest` (Bean Validation del design D9), 201 o 200 con `Location` y el esquema de seguridad `apiKey` en OpenAPI; verificar con tests de integración los escenarios de los requisitos "Alta de cliente", "Normalización del teléfono" y "Validación de los datos de alta", y que `/v3/api-docs` documenta el endpoint con su seguridad

## 6. Consulta y búsqueda

- [x] 6.1 `ConsultaClienteService.obtener(id)` y `GET /clientes/{id}` con `ETag` débil (design D7); verificar con tests de integración los escenarios de "Consulta de la ficha del cliente" y el 404
- [x] 6.2 `ConsultaClienteService.buscar(...)`, `FiltroBusqueda` y su implementación con `Specification` (design D8), con `PaginaResponse` y `ClienteResumenResponse`; verificar con tests de integración todos los escenarios de "Búsqueda paginada de clientes", "Búsqueda por nombre sin distinguir mayúsculas ni acentos" y "Tamaño de página limitado", incluido el orden por `ultimoContactoEn`

## 7. Integración y documentación

- [x] 7.1 Documentar en el `README` la variable `CLIENTES_CLAVE_LEADS` y el contrato para `leads` (header, campos, 200/201, errores); verificar que `docker compose up` con `.env` completo levanta el servicio y que un `POST /clientes` con `curl` y la clave responde 201
- [x] 7.2 Correr la suite completa (`mvn verify`) y `openspec validate clientes-02-registro-y-consulta --strict`; verificar que los dos pasan sin errores
