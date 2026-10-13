# 03 - Modelo de datos, estados y auditoría

## 1. Estados (máquina de estados)

```
RECEIVED ──► SENT_TO_ABM ──► APPROVED        (E4)
              │            ├─► REJECTED       (E5, rechazo funcional)
              │            ├─► FAILED         (falla técnica, reintentos agotados)
              │            └─► TIMEOUT        (ABM sin respuesta, reintentos agotados)
              │
RECEIVED ─────┴─► VALIDATION_FAILED          (E2, rechazo inmediato en el borde)
```

Terminales: `APPROVED`, `REJECTED`, `FAILED`, `TIMEOUT`, `VALIDATION_FAILED`.
Los reintentos no crean estados nuevos: vuelven a `RECEIVED` con `next_attempt_at` (backoff).

- **Rechazo funcional** (`REJECTED`) = ABM entendió y decidió "no" → no se reintenta.
- **Falla técnica** (`FAILED`/`TIMEOUT`) = no hubo respuesta válida → se reintenta y luego termina.

## 2. Tablas

### nomination
| Columna | Tipo | Notas |
|---|---|---|
| id | UUID (PK) | `nomination_id` |
| customer_id | varchar(64) | |
| account_ref | varchar(128) | referencia opaca para ABM; nunca se expone completa |
| card_token | varchar(256) | **token**; nunca PAN |
| card_mask | varchar(16) | `****1234` |
| alias | varchar(32) | |
| request_id | UUID | id de la solicitud del cliente |
| status | varchar(24) | enum |
| result | varchar(24) | enum normalizado |
| external_ref | varchar(128) | referencia de ABM |
| reason_code / reason_message | varchar | motivo normalizado |
| attempts | int | intentos contra ABM |
| next_attempt_at | timestamptz | backoff de reintento |
| correlation_id | varchar(64) | trazabilidad |
| created_at / updated_at / sent_to_abm_at / completed_at | timestamptz | |
| version | bigint | bloqueo optimista |

Índices: `status`, `request_id`, `correlation_id`.

### nomination_status_history (auditoría, inmutable)
`id`, `nomination_id`, `from_status`, `to_status`, `reason_code`, `message`, `actor`
(`API`/`WORKER`/`ABM`/`SYSTEM`), `correlation_id`, `occurred_at`.
Solo INSERT: nunca UPDATE ni DELETE. Permite reconstruir la trazabilidad end-to-end.

### idempotency_record
`id`, `idempotency_key` (**unique**), `request_hash` (SHA-256), `nomination_id`,
`response_status`, `response_body`, `created_at`, `expires_at`.
La huella detecta reuso de la clave con un cuerpo distinto → 409.

### outbox_event
`id` (= `eventId`, PK), `aggregate_type`, `aggregate_id`, `event_type`, `event_version`,
`payload` (text/JSON), `status` (`PENDING`/`PROCESSING`/`PUBLISHED`/`FAILED`/`DLQ`),
`attempts`, `available_at`, `last_error`, `created_at`, `published_at`, `version`.
Índice compuesto `(status, available_at)`.

## 3. Restricciones de unicidad

- `idempotency_record.idempotency_key` → **unique** (evita doble ingreso).
- `outbox_event.id` = `eventId` → la deduplicación de eventos en consumidores se apoya en esta clave.
- `nomination.id` → PK.
- `nomination.version` → control de concurrencia optimista (evita doble despacho y doble transición).

## 4. Protección de datos

- No se persiste PAN ni datos sensibles completos. `card_id` se guarda como **token** (`card_token`) y
  la vista pública solo expone `card_mask`.
- `account_id` se conserva como `account_ref` (referencia opaca necesaria para ABM) y se expone enmascarado.
- Los logs nunca incluyen `card_token`, `account_ref` completo ni credenciales.
