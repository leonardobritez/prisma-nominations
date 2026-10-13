# PRISMA - Solución de nominación de cuentas y tarjetas

Prueba técnica del área **APIs**. Solución en **Java 21 / Spring Boot 3.3** que permite a entidades
financieras solicitar la nominación de cuentas y tarjetas, procesarla de forma **asincrónica** contra
el sistema externo **ABM** y publicar **un único evento de resultado** por operación.

> Caso ficticio y simplificado. No contiene datos reales de clientes, tarjetas, cuentas ni credenciales.

## Arquitectura en una imagen

```
                 POST /v1/nominations (202)
   Cliente  ───────────────────────────────►  ┌───────────────────┐
                                              │   nomination-api   │  valida, idempotencia,
                                              │  (borde síncrono)  │  persiste RECEIVED + historial
                                              └─────────┬──────────┘
                                                        │  (misma base transaccional)
                                              ┌─────────▼──────────┐
                                              │  PostgreSQL        │
                                              │  nomination        │
                                              │  history (auditoría)│
                                              │  idempotency_record │
                                              │  outbox_event      │
                                              └─────────┬──────────┘
                                                        │
                          ┌─────────────────────────────┴───────────────────────────┐
                          │                                                           │
              ┌───────────▼────────────┐                              ┌─────────────▼─────────────┐
              │   nomination-worker     │  POST (async)               │    event-publisher         │
              │  polling RECEIVED       ├──────────►  ABM             │  relay del outbox          │
              │  timeout/reintentos/CB  │  ◄──── callback/idempotente  │  reintentos + DLQ          │
              │  escribe estado + outbox│                              └─────────────┬─────────────┘
              └─────────────────────────┘                                            │ publica
                                                                        ┌────────────▼────────────┐
                                                                        │  Kafka (o sink de log)   │
                                                                        │  nomination.result (v1)  │
                                                                        └──────────────────────────┘
```

## Módulos

| Módulo | Tipo | Responsabilidad |
|---|---|---|
| `common` | librería | Contratos compartidos: estados, eventos, errores, correlación, enmascaramiento |
| `nomination-persistence` | librería | Modelo de datos transaccional: entidades, repositorios y outbox |
| `nomination-api` | servicio | Borde síncrono: validación, idempotencia de ingreso, consulta de estado/historial |
| `nomination-worker` | servicio | Procesamiento asincrónico: envío a ABM, máquina de estados, resiliencia, outbox |
| `event-publisher` | servicio | Publicación del evento de resultado: relay del outbox, reintentos y DLQ |

> Son **3 microservicios** desplegables. `common` y `nomination-persistence` son **librerías compartidas**
> (kernel técnico), no servicios.

## Requisitos

- Java 21
- Maven 3.8+
- (Opcional) Docker para PostgreSQL y Kafka: `docker compose up -d`

## Cómo correr

```bash
# 1) Infraestructura (PostgreSQL + Kafka/Redpanda + consola)
docker compose up -d

# 2) Compilar y testear todo
mvn verify

# 3) Levantar los servicios (3 terminales).
#    El contenedor publica Postgres en 55432 (para no chocar con un Postgres local en 5432).
$env:DB_URL="jdbc:postgresql://localhost:55432/nominations"
mvn -pl nomination-api     spring-boot:run
mvn -pl nomination-worker  spring-boot:run          # por defecto perfil mock-abm
mvn -pl event-publisher    spring-boot:run -Dspring-boot.run.profiles=kafka
```

Sin Docker: la suite de pruebas usa **H2 en memoria** y no requiere infraestructura (`mvn test`).

Al arrancar, la API aplica las **migraciones Flyway** (`db/migration/V1__init.sql`) y crea el esquema;
worker y publisher validan que ya esté en `v1` (`ddl-auto=none`).

## Resiliencia y protección (implementado)

- **Idempotencia** de ingreso (`Idempotency-Key`), en la integración con ABM (`@Version` + callback
  idempotente) y en la publicación (`eventId`).
- **Reintentos** con backoff exponencial + jitter, **timeout** y sweeper de recuperación.
- **Circuit breaker** (Resilience4j, instancia `abm`) sobre la llamada a ABM → falla rápido ante
  degradación sostenida (métrica `resilience4j_circuitbreaker_state`).
- **Rate limiting** en la API (429 + `Retry-After`, env `RATE_LIMIT_RPM`, por defecto 120/min).
- **Outbox transaccional** (estado ⇔ evento) + **DLQ** en el publisher.

## Probar la solución en 1 minuto

```bash
curl -i -X POST http://localhost:8081/v1/nominations \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer dev-token" \
  -H "Idempotency-Key: mi-clave-unica-1" \
  -H "X-Correlation-Id: corr-demo-1" \
  -d '{"customer_id":"123456","account_id":"987654","card_id":"TOKEN_OR_MASKED_REFERENCE","alias":"CUENTA_PRINCIPAL","request_id":"8fcb3fa0-7f19-4fc2-a803-cc76ac815001"}'
```

El worker (perfil `mock-abm`) procesa la solicitud y entrega el resultado por callback; el publisher
publica el evento. Consultá el estado:

```bash
curl http://localhost:8081/v1/nominations/{nomination_id}
curl http://localhost:8081/v1/nominations/{nomination_id}/history
```

Sufijos de `account_id` en el simulador de ABM: `_REJ` (rechazo), `_TO` (timeout), `_DUP` (respuesta
duplicada), `_FAIL` (falla técnica recuperable).

## Documentación

| Documento | Contenido |
|---|---|
| [docs/01-arquitectura.md](docs/01-arquitectura.md) | Componentes, fronteras y flujo end-to-end |
| [docs/02-contratos.md](docs/02-contratos.md) | Endpoints, contratos, códigos HTTP y modelo de errores |
| [docs/03-modelo-datos.md](docs/03-modelo-datos.md) | Estados, auditoría y restricciones de unicidad |
| [docs/04-mensajeria-eventos.md](docs/04-mensajeria-eventos.md) | Diseño de mensajería y contrato de eventos |
| [docs/05-idempotencia-resiliencia.md](docs/05-idempotencia-resiliencia.md) | Idempotencia, reintentos, timeout, circuit breaker, DLQ |
| [docs/06-seguridad.md](docs/06-seguridad.md) | Autenticación, autorización y protección de datos |
| [docs/07-observabilidad.md](docs/07-observabilidad.md) | Métricas, logs, trazas, tableros y alertas |
| [docs/08-pruebas-despliegue.md](docs/08-pruebas-despliegue.md) | Estrategia de pruebas, despliegue y rollback |
| [docs/09-decisiones-riesgos.md](docs/09-decisiones-riesgos.md) | Supuestos, decisiones y riesgos conocidos |
| [docs/10-escenarios-y-preguntas.md](docs/10-escenarios-y-preguntas.md) | Escenarios E1–E10 y respuestas a las 12 preguntas |
