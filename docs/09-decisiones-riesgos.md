# 09 - Supuestos, decisiones, alternativas descartadas y riesgos

## 1. Supuestos

- ABM es un sistema externo que: (a) acepta el envío de forma asincrónica, (b) entrega el resultado
  después (callback o sondeo) y (c) puede demorar varios minutos.
- La respuesta de ABM es programática y categorizable en resultado + motivo.
- El `card_id` recibido ya es un token o referencia enmascarada (la plataforma no tokeniza PAN).
- Existe una base relacional disponible y compartible por API/worker/publisher (en la solución de demo).
- Las operaciones son de negocio financiero, por lo que la exactitud y la auditabilidad priman sobre la latencia extrema.

## 2. Decisiones (con justificación)

| Decisión | Por qué |
|---|---|
| 3 servicios (API / worker / publisher) | Separan ritmo de cambio, modo de fallo y perfil de carga |
| **Transactional Outbox** | Consistencia estado⇔evento sin 2PC ni transacciones distribuidas |
| Java 21 + Spring Boot 3.3 | Madurez, ecosistema, LTS, lenguaje moderno (records, virtual threads) |
| PostgreSQL | Transaccional, constraints de unicidad, robustez para auditoría |
| Kafka (con fallback a log) | Desacople, orden por partición, replay; transporte sustituible |
| Bloqueo optimista (`@Version`) | Evita doble despacho/procesamiento entre instancias sin locks distribuidos |
| Callback idempotente de ABM | Cubre E7 y reentregas at-least-once |
| Reintentos exponenciales + jitter + circuit breaker | Tolerancia a fallas parciales sin martillar ABM |
| DLQ | Aísla eventos no publicables y permite reproceso con trazabilidad |
| Estados explícitos y terminales | Claridad de máquina de estados y distinción funcional vs. técnica |
| Motivos normalizados | No filtrar texto crudo de ABM; contrato estable para consumidores |
| Versionado por ruta + `eventVersion` | Evolución sin romper consumidores |

## 3. Alternativas descartadas

| Alternativa | Por qué se descartó |
|---|---|
| Monolito | Acopla disponibilidad del borde a ABM y al broker |
| Microservicio por función | Fragmentación excesiva sin límites de dominio claros |
| 2PC / XA entre estado y publicación | Complejo, frágil y costoso; el outbox resuelve el mismo problema |
| Publicar dentro de la transacción de negocio | El broker no participa de la transacción → riesgo de inconsistencia |
| Polling sincrónico de ABM bloqueando el request | Rompe el requisito de no bloquear al consumidor |
| Guardar el PAN completo | Violación de PCI-DSS; se usa token + máscara |
| Reintentar rechazos funcionales | No cambia el resultado; genera ruido y carga |
| `eventId` autogenerado en el broker | Dificulta la deduplicación; se usa `eventId` propio en el outbox |

## 4. Riesgos conocidos y mitigación

| Riesgo | Mitigación |
|---|---|
| Base compartida entre servicios (acoplamiento) | Frontera clara por schema/rol; alternativa: CDC (Debezium) sobre el outbox |
| Entrega at-least-once puede duplicar en consumidores | Deduplicación por `eventId`; documentado en el contrato |
| Crecimiento de la tabla de outbox | Archivado/purga de publicados; particionado por fecha |
| ABM sin respuesta prolongada | Timeout + sweeper + circuit breaker + DLQ |
| Reintentos que amplifican carga | Backoff + jitter + circuit breaker |
| Idempotencia si el cliente no envía clave | Se usa `request_id`; se documenta la recomendación de enviar `Idempotency-Key` |
| Deriva de esquema de eventos | Schema Registry + reglas de compatibilidad |
| Picos de volumen (E10) | Escalado horizontal sin estado + reclamo optimista + backpressure |
| Fuga de datos en logs | Exclusión explícita de campos sensibles en el logging |

## 5. Qué cambiaría al multiplicar por 100 el volumen (E10)

- Escalar réplicas de API/worker/publisher; particionar el outbox y las consultas por fecha/estado.
- Aumentar particiones de Kafka para paralelizar consumidores.
- Introducir archiving de `nomination`/`outbox` históricos y réplicas de lectura para consultas.
- Ajustar pools de conexión, batch sizes y ventanas de polling; métricas y autoscaling por backlog.
