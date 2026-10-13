# 10 - Escenarios obligatorios (E1–E10) y respuestas a las preguntas mínimas

## 1. Escenarios E1–E10

| ID | Escenario | Comportamiento esperado | Dónde | Cubierto por |
|---|---|---|---|---|
| E1 | Solicitud válida | 202, `RECEIVED`, trazabilidad | `nomination-api` | `e1_validRequestIsAcceptedAndTraced` |
| E2 | Datos obligatorios ausentes / formato | 400 con error claro y consistente | `nomination-api` | `e2_missingFieldsReturnsBadRequest`, `e2b_invalidFormatReturnsBadRequest` |
| E3 | Solicitud repetida | Idempotente: mismo `nomination_id`, sin doble envío ni evento | `nomination-api` | `e3_duplicateRequestIsIdempotent`, `e3b_sameKeyDifferentBodyConflicts` |
| E4 | ABM aprueba | `APPROVED` + evento único | `nomination-worker` | `e4_abmApproves` |
| E5 | ABM rechaza por regla funcional | `REJECTED`, motivo normalizado, evento | `nomination-worker` | `e5_abmRejectsWithNormalizedReason` |
| E6 | ABM no responde | Timeout, reintentos limitados, recuperación | `nomination-worker` | `e6a_timeoutAfterExhaustedRetries`, `e6b_timeoutSchedulesRetry` |
| E7 | ABM envía respuesta duplicada | Procesamiento idempotente del retorno | `nomination-worker` | `e7_duplicateCallbackIgnored` |
| E8 | Falla al publicar el evento | Consistencia estado⇔publicación | `event-publisher` | `e8_successfulPublication`, `e8b_failureLeadsToDlq` |
| E9 | Consumidor temporalmente fuera de servicio | Entrega desacoplada y recuperación | `event-publisher` | `e9_consumerRecoversWithoutLoss` |
| E10 | Pico de volumen | Escalado sin perder trazabilidad | Arquitectura | Ver `docs/01` §5 y `docs/09` §5 |

## 2. Respuestas a las preguntas mínimas

**1. ¿Cuál es la frontera de responsabilidad de cada componente?**
API: validación, idempotencia de ingreso, persistencia inicial y consultas. Worker: envío a ABM,
resiliencia, máquina de estados y outbox. Publisher: publicación del evento, reintentos y DLQ.
La base es la fuente de verdad; el broker solo transporta. (Ver `docs/01` §1.)

**2. ¿Qué devuelve la API al aceptar una operación asincrónica?**
**202 Accepted** con `nomination_id`, `request_id`, `status = RECEIVED`, `created_at`, `status_url` y
cabecera `Location`. No espera a ABM.

**3. ¿Cómo se define la clave de idempotencia y cuánto tiempo se conserva?**
Header `Idempotency-Key` (fallback `request_id`). Se persiste única con huella SHA-256 del cuerpo.
Retención por defecto **24 h** (configurable), mayor que el mayor timeout razonable del cliente.

**4. ¿Cómo se evita publicar un evento si la transacción de negocio no quedó confirmada?**
Patrón **Outbox transaccional**: el estado terminal y el `outbox_event` se escriben en la **misma
transacción**. Si la transacción falla, no hay ni estado ni evento. La publicación ocurre después,
fuera de la transacción, leyendo el outbox.

**5. ¿Cómo se procesa de forma segura una respuesta duplicada de ABM?**
El callback es idempotente: si la nominación ya es terminal, se ignora (no cambia estado ni publica un
segundo evento). Se apoya además en `eventId` para deduplicar la publicación.

**6. ¿Cómo se distingue un rechazo funcional de una falla técnica?**
Rechazo funcional → `REJECTED` (motivo normalizado, **no** reintentable). Falla técnica → `FAILED`/`TIMEOUT`
(con reintentos). La distinción se basa en el tipo de respuesta (regla de negocio vs. timeout/5xx/error).

**7. ¿Qué política de reintentos se aplica y qué errores no deberían reintentarse?**
Backoff exponencial con jitter, máximo configurable. No se reintentan: errores de validación (4xx del
cliente) ni rechazos funcionales de ABM. Sí se reintentan: timeout, 5xx, 429.

**8. ¿Cómo se consulta el estado actual y el historial?**
`GET /v1/nominations/{id}` (estado actual) y `GET /v1/nominations/{id}/history` (historial de cambios).
Ambos enmascaran datos sensibles.

**9. ¿Cómo se correlaciona una solicitud desde el canal hasta el evento final?**
`X-Correlation-Id` (generado si falta) → `nomination.correlation_id` → logs → llamada a ABM → callback →
evento (`correlationId`). Con ese id se reconstruye el recorrido punta a punta.

**10. ¿Cómo se versionan APIs y eventos sin romper consumidores?**
API versionada por ruta (`/v1`). Eventos con `eventVersion` y topic versionado. Solo cambios aditivos
compatibles; cambios incompatibles → nueva versión con doble publicación; Schema Registry en CI.

**11. ¿Qué indicadores permiten detectar degradación antes de un incidente?**
`nominations.pending`/`inflight` (backlog), tasa de reintentos/timeouts, estado del circuit breaker,
`outbox.pending` y antigüedad, tamaño de DLQ, tasa de duplicados. (Ver `docs/07` §3.)

**12. ¿Qué cambia al multiplicar por cien el volumen?**
Escalado horizontal (API/worker/publisher sin estado), más particiones de Kafka, particionado/archivado
de `nomination`/`outbox`, réplicas de lectura, ajuste de pools/batch/polling y autoscaling por backlog.
(Ver `docs/09` §5.)

## 3. Cómo verificar todo localmente

```bash
mvn verify    # 14 pruebas de integración (E1–E9) + compilación
```

Los escenarios E1–E9 quedan cubiertos por pruebas automatizadas; E10 es una propiedad de diseño
documentada (escalado horizontal + reclamo optimista + backpressure).
