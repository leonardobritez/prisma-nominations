# 04 - Mensajería y contrato de eventos

## 1. Topología

| Topic | Uso |
|---|---|
| `prisma.nominations.result.v1` | Evento único de resultado por nominación |
| `prisma.nominations.result.v1.dlq` | Dead-letter queue de eventos no publicables |

Partition key = `nominationId` → garantiza orden por agregado y permite escalar consumidores.
El transporte por defecto en demo es un sink de log; con el perfil `kafka` se publica en Kafka/Redpanda.
El diseño no depende del broker: cualquier cola administrada (SQS/SNS, Service Bus, Pub/Sub) es sustituible
implementando `EventPublisher`.

## 2. Contrato del evento `nomination.result`

```json
{
  "eventId": "0d5f...",
  "eventType": "nomination.result",
  "eventVersion": 1,
  "nominationId": "b1e2...",
  "customerId": "123456",
  "accountId": "****7654",
  "cardMask": "****ENCE",
  "alias": "CUENTA_PRINCIPAL",
  "status": "APPROVED",
  "result": "APPROVED",
  "reasonCode": null,
  "reasonMessage": null,
  "attempts": 1,
  "correlationId": "corr-demo-1",
  "occurredAt": "2026-10-11T23:25:49Z"
}
```

- **Un solo evento terminal** por nominación (`APPROVED`/`REJECTED`/`FAILED`/`TIMEOUT`).
- `eventId` es la **clave de deduplicación** en consumidores (entrega at-least-once).
- Sin datos sensibles completos: identificadores enmascarados.
- `correlationId` permite correlacionar canal → evento final.

## 3. Entrega y garantías

- Productor Kafka: `acks=all` + `enable.idempotence=true` → sin pérdida y sin duplicados en el broker.
- El relay espera la confirmación del broker (`future.get`) antes de marcar `PUBLISHED`.
- Si la publicación falla, el evento permanece en el outbox y se reintenta → **at-least-once efectivo**.
- Deduplicación en el consumidor por `eventId`.

## 4. Versionado de eventos (compatibilidad evolutiva)

- `eventVersion` entero; el topic incluye `v1`.
- Reglas: **solo agregar campos opcionales**; nunca renombrar, reordenar con significado ni cambiar tipos.
- Consumidores: ignoran campos desconocidos; no fallan por adiciones.
- Cambios incompatibles → `eventVersion` nuevo + topic `v2`, con doble publicación durante la transición.
- En producción se recomienda **Schema Registry** (Avro/Protobuf) para validar compatibilidad en CI.
