# 01 - Arquitectura y flujo end-to-end

## 1. Frontera de responsabilidad de cada componente

| Componente | Hace | No hace |
|---|---|---|
| **nomination-api** | Validar, aplicar idempotencia de ingreso, persistir estado inicial `RECEIVED`, exponer consultas de estado e historial, autenticar/autorizar | No llama a ABM, no publica eventos, no decide estados finales |
| **nomination-worker** | Enviar a ABM, aplicar resiliencia (timeout/reintentos/circuit breaker), ejecutar la máquina de estados y escribir el evento en el outbox en la misma transacción | No expone API pública, no publica al broker |
| **event-publisher** | Leer el outbox, publicar el evento de resultado, reintentar y enviar a DLQ | No llama a ABM, no decide estados de negocio |
| **PostgreSQL** | Fuente de verdad transaccional (estado, historial, idempotencia, outbox) | — |
| **Kafka** | Transporte desacoplado de eventos de resultado | No es fuente de verdad |

La regla de oro: **el estado de negocio vive en la base; el evento vive en el outbox; el broker solo transporta.**

## 2. Flujo end-to-end

```
1. Cliente → POST /v1/nominations (con Idempotency-Key)
2. API: valida → calcula/verifica idempotencia → TX:
      insert nomination (RECEIVED)
      insert history (→ RECEIVED)
      insert idempotency_record
   responde 202 Accepted + Location + nomination_id
3. Worker (polling): toma RECEIVED → TX corta: RECEIVED → SENT_TO_ABM (intento++)
4. Worker → ABM (POST async, fuera de TX), propaga X-Correlation-Id
5a. ABM acepta (202): se guarda external_ref
5b. ABM rechaza en el envío (4xx): TX → REJECTED (no reintentable)
5c. Falla técnica (timeout/5xx): TX → RECEIVED + next_attempt_at (backoff) o FAILED si se agotan
6. ABM entrega el resultado por callback (idempotente):
      si ya es terminal → se ignora (duplicado)
      si no → TX: estado terminal + history + insert outbox_event
7. Publisher (relay): outbox PENDING → publica en Kafka → PUBLISHED
      ante fallo: reintentos con backoff → DLQ
8. Consultas: GET estado actual y GET historial (trazabilidad punta a punta por correlation_id)
```

## 3. Por qué 3 servicios (y no 1 ni 10)

El criterio es **ritmo de cambio, modo de fallo y perfil de carga**:

- El **borde síncrono** (API) debe responder en milisegundos y escalar con el tráfico de entrada.
- El **procesamiento** (worker) tiene latencias de minutos, depende de un tercero y usa reintentos: su
  escalado y su operación son distintos.
- La **publicación** (publisher) aísla el broker y permite evolucionar el transporte sin tocar la lógica.

Un monolito acoplaría la disponibilidad del borde a la del procesamiento y del broker. Un servicio por
función (validar, persistir, enviar, publicar) fragmentaría en exceso sin límites de dominio claros.

## 4. Patrón de consistencia

Se usa **Transactional Outbox**: el cambio de estado y la escritura del evento ocurren en la **misma
transacción**. Esto garantiza que nunca exista un estado terminal sin evento (ni un evento sin estado),
sin necesidad de 2PC ni de transacciones distribuidas. El evento se publica de forma asíncrona y
at-least-once; los consumidores deduplican por `eventId`.

## 5. Escalabilidad (E10)

- **API**: stateless → escalado horizontal tras un balanceador.
- **Worker**: múltiples instancias; el reclamo usa **bloqueo optimista** (`@Version`), de forma que dos
  instancias no procesan la misma nominación. El particionado natural es por `nomination_id`.
- **Publisher**: reclamo optimista del outbox; una sola instancia publica cada evento.
- **Base**: índices por `status`, `next_attempt_at`, `outbox(status, available_at)`.

Ver también: `docs/05-idempotencia-resiliencia.md` y `docs/09-decisiones-riesgos.md`.
