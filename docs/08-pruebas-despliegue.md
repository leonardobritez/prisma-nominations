# 08 - Estrategia de pruebas, despliegue y operación

## 1. Pirámide de pruebas

| Nivel | Qué cubre | En el repo |
|---|---|---|
| Unitarias | Reglas de validación, enmascaramiento, máquina de estados, política de reintentos | (base) |
| Integración | API + H2; worker + maquina de estados; outbox + relay | `*-IntegrationTest` |
| Contrato | OpenAPI/JSON del evento; compatibilidad de esquema | (extensible) |
| End-to-end | Flujo completo API → worker → callback → publisher | (demo con docker-compose) |
| Carga | Escalabilidad 10k→1M/día y picos (E10) | `scripts/load-test.js` (k6) |

## 2. Casos obligatorios (E1–E10)

Los 10 escenarios están cubiertos por las pruebas de integración (ver
`docs/10-escenarios-y-preguntas.md`). Ejecución:

```bash
mvn verify        # compila, testea y empaqueta todo
```

Todas las pruebas usan **H2 en memoria** (perfil `test`) y no requieren infraestructura.

## 2.b Prueba de carga (k6, E10)

`scripts/load-test.js` modela 10k→1M/día y picos con un executor de tasa de llegada
(`ramping-arrival-rate`) y umbrales (`http_req_failed < 1%`, `p95 < 800 ms`).

```bash
# Para medir capacidad real, desactivar el rate limiting de la API:
$env:RATE_LIMIT_ENABLED="false"   # y reiniciar la API
k6 run scripts/load-test.js
k6 run -e BASE_URL=http://localhost:8081 scripts/load-test.js
```

Palancas al escalar ×100: más réplicas de api/worker/publisher (stateless), más particiones de Kafka,
particionado/archivado de `nomination`/`outbox`, réplicas de lectura para consultas y autoscaling por backlog.

## 3. Pruebas end-to-end manuales

Con `docker compose up -d` y los 3 servicios levantados (worker con perfil `mock-abm`), los sufijos de
`account_id` reproducen los escenarios:

| Sufijo `account_id` | Escenario |
|---|---|
| (ninguno) | E1/E4 aprobación |
| `_REJ` | E5 rechazo funcional |
| `_TO` | E6 timeout / recuperación |
| `_DUP` | E7 respuesta duplicada |
| `_FAIL` | falla técnica + reintento |

> En esta máquina el PostgreSQL local ocupaba el 5432, por eso el contenedor publica **55432** y los
> servicios se levantan con `--spring.datasource.url=jdbc:postgresql://localhost:55432/nominations`.
> La API aplica **Flyway** al arrancar (crea el esquema); worker y publisher validan que ya esté en `v1`.
> El `event-publisher` no expone servidor HTTP (es un worker de relay); se verifica por su métrica
> `outbox_published_total` o consumiendo el topic en Redpanda:
> `docker exec prisma-redpanda rpk topic consume prisma.nominations.result.v1 --offset start --num 10`.
> Rate limiting verificable con una ráfaga de `POST /v1/nominations` (comienza a responder 429 + `Retry-After`).

**Resultado observado en la corrida E2E**: aprobación→`APPROVED`, `_REJ`→`REJECTED` (CUSTOMER_BLOCKED),
`_FAIL`→`APPROVED` tras 1 reintento (attempts=2), `_DUP`→ 1 solo evento, `_TO`→ `SENT_TO_ABM`
(callback pendiente), idempotencia→ mismo `nomination_id`, validación→ 400. **5 eventos** publicados en
`prisma.nominations.result.v1`, con datos enmascarados y `correlationId`.

## 4. Despliegue

- **Artefactos**: jar ejecutable por servicio (Spring Boot) → imagen de contenedor.
- **CI/CD**: build → tests → análisis → imagen → despliegue.
- **Migraciones de base versionadas con Flyway**: las migraciones viven en
  `nomination-persistence/src/main/resources/db/migration` (`V1__init.sql`) y viajan con la librería, de
  modo que cualquier servicio que arranque primero crea el esquema (lock de Flyway) y el resto valida
  `v1`. Los servicios usan `spring.jpa.hibernate.ddl-auto=none`; en tests Flyway se desactiva y H2 crea
  el esquema (`create-drop`). Cambios futuros: `V2__...sql`, aditivos y reversibles.
- **Configuración por entorno**: variables de entorno (`DB_URL`, `KAFKA_BOOTSTRAP`, `SECURITY_ENABLED`, …).
- **Escalado**: API y worker sin estado → réplicas horizontales; publisher con reclamo optimista.
- **Despliegue progresivo**: rolling update / canary; versionado de API (`/v1`) y eventos (`eventVersion`).

## 5. Rollback

- Rollback por **imagen/versión** anterior (inmutable). Al ser contenedores sin estado, el rollback es inmediato.
- Los eventos y contratos son **compatibles hacia atrás**; un rollback no debe romper consumidores.
- Migraciones de base: cambios aditivos y reversibles; nunca destructivos en el mismo release.
- Ante DLQ/inconsistencias: reproceso controlado desde el outbox (fuente de verdad).

## 6. Operación

- **Runbooks** por alerta (ver `docs/07-observabilidad.md`): backlog, circuit breaker abierto, DLQ, 5xx.
- **Reproceso**: reencolar eventos de DLQ/outbox sin pérdida, idempotente por `eventId`.
- **Capacidad**: monitorear `nominations.pending`, latencia ABM y saturación de workers para escalar antes del incidente.
- **Ventanas de mantenimiento**: ABM puede degradarse; el circuit breaker y los reintentos absorben la carga.

## 7. Cómo evoluciona el contrato (resumen)

1. Cambios **aditivos y opcionales** se despliegan sin coordinar consumidores.
2. Cambios incompatibles → nueva versión (`/v2`, `eventVersion=2` + topic `v2`) con **doble publicación** transitoria.
3. Los consumidores ignoran campos desconocidos y deduplican por `eventId`.
4. Schema Registry en CI bloquea cambios incompatibles antes de llegar a producción.
