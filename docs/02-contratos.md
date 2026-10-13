# 02 - Contratos de API, códigos HTTP y modelo de errores

## 1. Endpoints

### POST /v1/nominations

Crea una solicitud de nominación. **Asincrónico**: acepta y responde sin esperar a ABM.

Cabeceras:

| Cabecera | Requerida | Descripción |
|---|---|---|
| `Authorization: Bearer <token>` | Sí | Autenticación |
| `Content-Type: application/json` | Sí | — |
| `Idempotency-Key` | No* | Clave de idempotencia de ingreso (*si falta se usa `request_id`) |
| `X-Correlation-Id` | No | Se genera uno si no viene; viaja en logs, trazas y evento |

Cuerpo:

```json
{
  "customer_id": "123456",
  "account_id": "987654",
  "card_id": "TOKEN_OR_MASKED_REFERENCE",
  "alias": "CUENTA_PRINCIPAL",
  "request_id": "8fcb3fa0-7f19-4fc2-a803-cc76ac815001"
}
```

Validaciones (E2): `customer_id`, `account_id`, `card_id`, `alias` no nulos ni vacíos; `alias` con patrón
`^[A-Z0-9_]+$`; `request_id` UUID válido; longitudes máximas por campo.

Respuesta **202 Accepted**:

```json
{
  "nomination_id": "b1e2...",
  "request_id": "8fcb3fa0-7f19-4fc2-a803-cc76ac815001",
  "status": "RECEIVED",
  "created_at": "2026-10-11T23:25:47Z",
  "status_url": "/v1/nominations/b1e2...",
  "message": "Solicitud aceptada para procesamiento asincronico."
}
```

Cabeceras de respuesta: `Location`, `X-Correlation-Id`, `X-Request-Id`.

### GET /v1/nominations/{nominationId}

Estado actual (200). Todos los identificadores sensibles van enmascarados:

```json
{
  "nomination_id": "b1e2...",
  "customer_id": "123456",
  "account_mask": "****7654",
  "card_mask": "****ENCE",
  "alias": "CUENTA_PRINCIPAL",
  "status": "APPROVED",
  "result": "APPROVED",
  "reason_code": null,
  "reason_message": null,
  "attempts": 1,
  "correlation_id": "corr-demo-1",
  "created_at": "2026-10-11T23:25:47Z",
  "updated_at": "2026-10-11T23:25:49Z",
  "completed_at": "2026-10-11T23:25:49Z",
  "history_url": "/v1/nominations/b1e2.../history"
}
```

### GET /v1/nominations/{nominationId}/history

Historial de cambios de estado (200):

```json
{
  "nomination_id": "b1e2...",
  "correlation_id": "corr-demo-1",
  "entries": [
    {"from_status": null, "to_status": "RECEIVED", "actor": "API", "occurred_at": "..."},
    {"from_status": "RECEIVED", "to_status": "SENT_TO_ABM", "actor": "WORKER", "occurred_at": "..."},
    {"from_status": "SENT_TO_ABM", "to_status": "APPROVED", "actor": "ABM", "occurred_at": "..."}
  ]
}
```

## 2. Códigos HTTP

| Código | Cuándo |
|---|---|
| 202 | Solicitud aceptada (creación) o replay idempotente |
| 200 | Consulta exitosa |
| 400 | Validación/Formato inválido (E2) |
| 401 | Falta o es inválido el token |
| 403 | Autenticado sin autorización |
| 404 | Nominación inexistente |
| 409 | `IDEMPOTENCY_CONFLICT`: misma clave con cuerpo distinto |
| 429 | Rate limit |
| 500 | Error interno (con `correlationId`) |

## 3. Modelo de error (estable, sin datos sensibles)

```json
{
  "timestamp": "2026-10-11T23:25:47Z",
  "status": 400,
  "code": "MISSING_FIELD",
  "message": "La solicitud contiene datos invalidos.",
  "path": "/v1/nominations",
  "correlationId": "corr-demo-1",
  "errors": [
    {"field": "customer_id", "code": "MISSING_FIELD", "message": "customer_id es obligatorio"}
  ]
}
```

Códigos (`ErrorCode`): `MISSING_FIELD`, `INVALID_FORMAT`, `INVALID_ALIAS`, `IDEMPOTENCY_CONFLICT`,
`IDEMPOTENCY_IN_PROGRESS`, `NOT_FOUND`, `UPSTREAM_TIMEOUT`, `UPSTREAM_ERROR`, `INTERNAL_ERROR`,
`UNAUTHORIZED`, `FORBIDDEN`, `RATE_LIMITED`.

> Los códigos son parte del contrato: no cambian de significado entre versiones.

## 4. Versionado de API

- Versionado por **ruta** (`/v1/...`). Cambios incompatibles → `/v2`.
- Campos nuevos opcionales no rompen consumidores (el cliente ignora desconocidos).
- Deprecación: se anuncian por cabecera `Deprecation`/`Sunset` y se mantiene convivencia de versiones.

## 5. Endpoint interno de callback de ABM

`POST /internal/v1/abm/callbacks` (solo worker, no expuesto públicamente):

```json
{"nomination_id":"b1e2...","external_ref":"ABM-b1e2","result":"APPROVED","reason":null}
```

Respuesta: `{"nomination_id":"b1e2...","outcome":"APPLIED"}` (`APPLIED` | `DUPLICATE_IGNORED` | `UNKNOWN`).
Es idempotente: reentregas no duplican estado ni evento. En producción se protege con mTLS/firma.
