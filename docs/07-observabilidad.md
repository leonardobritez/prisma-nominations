# 07 - Observabilidad: métricas, logs, trazas, tableros y alertas

## 1. Trazabilidad distribuida

- Todo request genera/transporta **`X-Correlation-Id`** y **`X-Request-Id`** (filtro en el borde).
- El `correlationId` se propaga: API → fila `nomination.correlation_id` → logs → llamada a ABM
  (`X-Correlation-Id`) → callback → evento (`correlationId`) → publisher.
- Trazas con **Micrometer Tracing + OpenTelemetry** (exportador OTLP); spans en API, worker y callbacks.
- Con el `correlationId` se reconstruye el recorrido completo desde el canal hasta el evento final.

## 2. Logs estructurados

- JSON (`logstash-logback-encoder`) con campos: `service`, `correlationId`, `requestId`, `nominationId`,
  `level`, `logger_name`, `timestamp`.
- Sin datos sensibles. Mensajes de eventos de negocio: creación, envío a ABM, transición, publicación, DLQ.

## 3. Métricas (Micrometer / Prometheus)

| Métrica | Tipo | Uso |
|---|---|---|
| `nominations.pending` | gauge | Backlog pendiente de envío (detección temprana de degradación) |
| `nominations.inflight` | gauge | Nominaciones enviadas sin resultado (posible ABM lento) |
| `nominations.retries` | counter | Tasa de reintentos (señal de inestabilidad de ABM) |
| `nominations.timeouts.retried` / `nominations.timedout` | counter | Problemas de timeout |
| `nominations.completed{result}` | counter | Volumen por resultado (tasa de aprobación) |
| `nominations.rejected` / `failed` | counter | Rechazos funcionales vs fallas técnicas |
| `nominations.callback.duplicate` | counter | Reentregas de ABM (E7) |
| `outbox.pending` | gauge | Backlog de publicación |
| `outbox.dlq` | gauge | Tamaño de la DLQ |
| `outbox.published` / `retried` / `deadlettered` / `recovered` | counter | Salud de la publicación |
| `nominations.events.enqueued` | counter | Eventos generados |

Disponibles en `/actuator/prometheus` de cada servicio.

## 4. Tableros (Grafana) sugeridos

- **Golden signals por servicio**: latencia, throughput, errores, saturación.
- **Nominaciones**: pendientes vs. en curso vs. completadas; distribución por `result`.
- **ABM**: ratio de reintentos/timeouts; estado del circuit breaker.
- **Outbox**: pendientes, antigüedad del evento más viejo, tasa de DLQ.

## 5. Alertas accionables

| Alerta | Condición sugerida | Acción |
|---|---|---|
| Backlog de envío crece | `nominations.pending` > umbral sostenido | Escalar workers / revisar ABM |
| ABM degradado | `nominations.retries` o timeout rate alto | Abrir circuit breaker / avisar |
| Circuito abierto | estado CB = OPEN | Revisar ABM, validar recuperación |
| Outbox envejece | `outbox.pending` > umbral o edad > N min | Revisar broker/publisher |
| DLQ con elementos | `outbox.dlq` > 0 | Inspeccionar y reprocesar |
| Duplicados altos | `nominations.callback.duplicate` elevado | Revisar integración con ABM |
| Errores 5xx | tasa 5xx > umbral | Revisar despliegue/logs con `correlationId` |

Las alertas deben ser **accionables**: cada una enlaza a un runbook y a un panel.

## 6. Health checks

- `/actuator/health` con probes de **liveness** y **readiness** para orquestadores (K8s).
- Readiness incluye conectividad a base de datos; la dependencia de ABM se monitorea vía circuit breaker
  (no debe tumbar el borde).
