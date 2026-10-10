# Proposal: CUIL y consentimiento de precalificación

## Why

Para precalificar crediticiamente a quien pide financiación hace falta su CUIL, un dato personal protegido por la Ley 25.326: el servicio tiene que validarlo, guardarlo cifrado, no exponerlo nunca completo y usarlo solo con el consentimiento del titular. Es además el paso previo del change 05 (situación crediticia), que `calificacion` necesita para la `ReglaFinanciacion` del MVP. Este change adapta el change 04 del documento semilla (§6.4) al contrato del equipo (`contratos-backend.md`, v1.1): el CUIL llega solo por el preformulario (`leads` → `POST /clientes`), la deduplicación base es por email (change 02) y nada se direcciona por CUIL.

**Criterio de alcance.** El contrato del equipo describe el MVP, no la versión final. Entra ahora lo que es caro de cambiar después (cifrado en reposo, índice ciego, modelo de consentimientos, forma del request acordada con `leads`) en su forma definitiva; los endpoints que se pueden sumar sin migrar datos quedan en el backlog.

## What Changes

- `POST /clientes` acepta `cuil` (opcional, con o sin guiones) y `consentimiento: true`. Errores: `CUIL_INVALIDO` (formato, prefijo de persona humana o dígito verificador), `CONSENTIMIENTO_REQUERIDO` (CUIL sin consentimiento) y `VALIDACION` (consentimiento sin CUIL).
- CUIL cifrado con AES-256-GCM, índice ciego HMAC-SHA256 (`cuil_hash`, único entre clientes activos) y últimos 4 dígitos del DNI para el enmascarado. El CUIL completo nunca sale del servicio.
- Consentimiento por finalidad, con versión del texto, origen y fechas. En este change existe una sola finalidad: `PRECALIFICACION_CREDITICIA`. Invariante: un cliente tiene CUIL si y solo si tiene ese consentimiento vigente.
- **Deduplicación por CUIL antes que por email.** El CUIL identifica a la persona aunque cambie de email. Si el email coincide con un cliente que tiene otro CUIL, o pertenece a otro cliente distinto del que coincide por CUIL, no se pisa nada y se marca `requiereRevision` con motivo `CUIL_DISTINTO` o `POSIBLE_DUPLICADO`. La respuesta del alta puede devolver `coincidencia` = `CUIL`.
- La ficha suma `cuil` (enmascarado `XX-XXXX5678-X`), `consentimientosVigentes` y `motivoRevision`.
- `PATCH /clientes/{id}` suma `telefonoAlternativo` (agregar o reemplazar) y `requiereRevision: false` (resolver una revisión).
- La anonimización también elimina el CUIL y revoca los consentimientos vigentes (las filas se conservan como constancia, sin datos personales).
- Migración `V3__cuil_y_consentimiento.sql` (el seed la numeraba V2; el change 03 usó ese número).
- **Desvíos respecto del seed §6.4:**
  - deduplicación CUIL → email en lugar de CUIL → teléfono;
  - el consentimiento llega como booleano (forma del contrato) y `clientes` completa finalidad, versión, origen y fecha;
  - pasan al backlog: `PUT /clientes/{id}/cuil`, `POST /clientes/busqueda-por-cuil`, `POST/DELETE /clientes/{id}/consentimientos` y la finalidad `CONTACTO_COMERCIAL` (la trae el change 07).
- **Fuera de alcance:** situación crediticia (05), revocación del consentimiento por el titular (se evalúa con el 05, cuando tenga efecto observable), auditoría de accesos al CUIL (backlog `clientes-11`), rotación de claves.

## Capabilities

### New Capabilities

- `clientes-privacidad`: tratamiento del CUIL (validación, cifrado, enmascarado, unicidad), consentimiento de precalificación y deduplicación del alta por CUIL con su marca de revisión.

### Modified Capabilities

- `clientes-gestion`: la ficha expone `cuil` enmascarado, `consentimientosVigentes` y `motivoRevision`; la edición suma `telefonoAlternativo` y la resolución de `requiereRevision`; la anonimización también elimina el CUIL y revoca los consentimientos.
- `clientes-eventos`: `ClienteActualizado` aclara que los cambios de teléfono alternativo, revisión, CUIL y consentimiento no publican eventos (no son datos que copie `leads`).

## Impact

- **Código (`clientes-service`):**
  - dominio: value object `Cuil`, `Consentimiento` dentro del agregado `Cliente`, puerto `CifradorCuil`, extensión de `ClienteFactory`, `SolicitudAlta`, `CambiosCliente` y `Coincidencia`;
  - aplicación: `AltaClienteService` (algoritmo CUIL → email y reintento por cualquiera de las claves únicas), `EdicionClienteService`, `AnonimizacionClienteService`;
  - infraestructura: `AesGcmCifradorCuil` en `infrastructure/crypto`, entidad y mapeo de consentimientos, consultas por `cuil_hash`;
  - API: `AltaClienteRequest`, `EdicionClienteRequest`, `ClienteResponse`, `CodigoError` y `ApiExceptionHandler`.
- **Configuración:** las claves `clientes.cuil.*` ya existen y se validan al arrancar (change 01); se suma `clientes.consentimiento.version-texto`.
- **`contracts`:** sin cambios (`CampoCliente` no cambia; la finalidad es un enum local).
- **`leads` (coordinar con su responsable):** reenviar `cuil` y `consentimiento` solo cuando el preformulario pide financiación y validar ahí que en ese caso son obligatorios; tratar los nuevos 422 y `coincidencia` = `CUIL`.
- **Front y contrato del equipo:** nuevos campos de la ficha y del PATCH; aclarar que el índice ciego es compatible con "no se indexa ni se direcciona por CUIL".
- **Seguridad:** el CUIL en claro existe solo en memoria durante el request; en la base solo hay texto cifrado, un HMAC y 4 dígitos.
