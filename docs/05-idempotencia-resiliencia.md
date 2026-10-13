# 05 - Idempotencia, reintentos, timeout, circuit breaker y DLQ

## 1. Clave de idempotencia

- Origen: header `Idempotency-Key`; si falta, se usa `request_id` del cuerpo.
- Alcance: por operación de creación de nominación.
- Persistencia: tabla `idempotency_record` con `idempotency_key` **único** + `request_hash` (SHA-256 del cuerpo).
- Retención: ventana configurable (`app.idempotency.ttl-hours`, por defecto **24 h**). Debe cubrir el
  mayor timeout razonable de reintento del cliente.
- Comportamiento:
  - Misma clave + mismo cuerpo → **replay** de la respuesta original (mismo `nomination_id`, 202), sin
    segundo envío a ABM ni segundo evento (E3).
  - Misma clave + cuerpo distinto → **409 IDEMPOTENCY_CONFLICT**.
  - Carrera entre instancias → resuelta por el índice único; el perdedor reproduce el resultado (E3).

## 2. Idempotencia en la integración con ABM

- El envío es "una vez por estado `RECEIVED`": el worker reclama con **bloqueo optimista** (`@Version`).
- El callback de ABM es **idempotente**: si la nominación ya es terminal, se ignora (E7) → no cambia
  estado ni publica un segundo evento.
- La deduplicación de la publicación usa `eventId` en el consumidor.

## 3. Reintentos

- **Política**: backoff **exponencial con jitter** (`base * 2^intentos` + jitter), máximo
  `app.abm.max-attempts` (por defecto 4).
- **Se reintenta**: timeout de red, 5xx, 429, respuestas sin resultado dentro de la ventana (`wait-window`).
- **No se reintenta**:
  - Validación (400): error del cliente.
  - Rechazo funcional de ABM (regla de negocio): reintentar no cambia el resultado → `REJECTED`.
  - 4xx definitivo en el envío → `REJECTED`.

## 4. Timeout y recuperación (E6)

- Cada envío tiene `timeout` (por defecto 3 s).
- Si ABM acepta pero no entrega resultado, la nominación queda `SENT_TO_ABM`; el **sweeper** la detecta tras
  `wait-window` (por defecto 30 s):
  - Si quedan intentos → vuelve a `RECEIVED` con backoff (reprograma el envío).
  - Si se agotaron → estado terminal `TIMEOUT` + evento.
- Un sweeper adicional recupera nominaciones colgadas en `PROCESSING` del outbox (caída entre reclamo y
  publicación).

## 5. Circuit breaker (implementado)

- Implementado con **Resilience4j**: instancia `abm` sobre la llamada a ABM, encapsulada en `AbmGateway`
  (`@CircuitBreaker(name = "abm", fallbackMethod = "onFailure")`). Objetivo: evitar cascadas cuando ABM está degradado.
- Comportamiento:
  - `AbmTechnicalException` (timeout/5xx/red) se **registra** como falla; al superar el umbral
    (`failure-rate-threshold: 50%`, `minimum-number-of-calls: 10`, ventana `20`) el circuito **abre**.
  - `AbmFunctionalException` (rechazo de negocio) queda en `ignore-exceptions`: no cuenta como falla ni abre el circuito.
  - Con el circuito **abierto**, las llamadas fallan rápido (`CallNotPermittedException` → convertida en
    `AbmTechnicalException`): no se martilla ABM y la máquina de estados reprograma el envío.
  - Cierre automático: `automatic-transition-from-open-to-half-open-enabled: true`,
    `wait-duration-in-open-state: 30s`, `permitted-number-of-calls-in-half-open-state: 5`.
- El circuit breaker y la política de reintentos son **complementarios** (no se solapan): el primero corta la
  avalancha; la segunda reintenta con backoff.
- Métricas asociadas: `resilience4j_circuitbreaker_state` (y health indicator), tasa de fallos y llamadas rechazadas.
- Configuración en `nomination-worker/src/main/resources/application.yml` (bloque `resilience4j.circuitbreaker.instances.abm`).
- Prueba: `WorkerProcessingIntegrationTest#circuitBreakerOpensOnSustainedFailures`.

## 6. Dead-letter queue (E8, E9)

- Tras agotar `app.publisher.max-attempts` (por defecto 5) de publicación, el evento pasa a `DLQ` y se
  publica también en el topic de DLQ (best-effort) para inspección/reproceso.
- El estado de negocio **ya quedó persistido**: la consistencia se conserva. La DLQ permite reprocesar la
  publicación sin perder trazabilidad.
- Los eventos en `PROCESSING` con antigüedad mayor a `stuck-threshold` se reencolan.

## 7. Resumen de garantías

| Aspecto | Garantía |
|---|---|
| Ingreso | Ex-cuando-idempotente (no doble envío ni doble evento) |
| Integración ABM | Idempotente por estado; callback deduplicado |
| Publicación | At-least-once + dedup por `eventId`; DLQ al agotar reintentos |
| Consistencia | Outbox transaccional (estado ⇔ evento) |
