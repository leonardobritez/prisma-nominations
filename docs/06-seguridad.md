# 06 - Seguridad, autenticación, autorización y protección de datos

## 1. Seguridad por diseño y mínimo privilegio

- **Autenticación**: la demo usa bearer token (`app.security.token`). En producción se reemplaza por
  **OAuth2/JWT** (resource server) con validación de firma, expiración, audiencia e issuer; el punto de
  extensión es idéntico (un filtro de autenticación).
- **Autorización**: solo el sujeto con `ROLE_NOMINATION_WRITER` accede a `/v1/nominations/**`.
  El diseño admite scopes por entidad financiera (aislamiento de tenant).
- **Transporte**: mTLS entre servicios y entre la plataforma y ABM. TLS extremo a extremo en el borde.
- **Mínimo privilegio**: cada servicio usa credenciales propias con permisos acotados sobre su esquema;
  el worker solo lee/escribe lo necesario; el publisher solo lee el outbox y escribe su estado.
- **CSRF**: deshabilitado (API stateless, sin cookies de sesión).

## 2. Protección de datos sensibles

- **Nunca** se persiste ni expone el PAN ni datos sensibles completos.
- `card_id` se almacena como **token** (`card_token`) y se expone enmascarado (`card_mask` = `****1234`).
- `account_id` se guarda como referencia opaca (`account_ref`) y se expone enmascarado.
- **En reposo**: cifrado a nivel de disco/volumen y, opcionalmente, cifrado a nivel de columna.
- **En tránsito**: TLS.
- **Logs**: structured logging con exclusión explícita de campos sensibles; nunca se loguea el token,
  la cuenta completa ni credenciales.
- **Tokenización/enmascaramiento**: la referencia de tarjeta es un token provisto por el proveedor; la
  API solo manipula el token y su máscara.

## 3. Superficie de ataque

| Servicio | Expuesto | Protección |
|---|---|---|
| nomination-api | Sí (público) | OAuth2/token, autorización por rol/tenant, rate limit |
| nomination-worker | No público (solo callback interno + actuator) | Red interna, mTLS, token de callback |
| event-publisher | Solo actuator | Red interna |
| Actuator | Interno | Restringido por red; endpoints sensibles no expuestos |

## 4. Rate limiting y abuso (implementado)

- `RateLimitFilter` (ventana fija de 1 minuto en memoria) limita el endpoint de escritura
  `POST /v1/nominations`, con sujeto `X-Client-Id` (o IP remota como aproximación).
- Respuesta al exceder: **429** con cabecera `Retry-After` (segundos restantes) y cuerpo
  `{"code":"RATE_LIMITED", ...}`; incrementa la métrica `api.ratelimited`.
- Configuración: `app.rate-limit.enabled` (por defecto `true`) y `app.rate-limit.requests-per-minute`
  (por defecto `120`; env `RATE_LIMIT_RPM`).
- En cluster el backend en memoria se reemplaza por uno distribuido (Redis/Bucket4j) **sin cambiar la
  frontera**: sigue siendo 429 + `Retry-After`.
- Tamaño máximo de payload y longitudes máximas por campo validadas en el borde.

## 5. Auditoría

- Todo cambio de estado queda en `nomination_status_history` con actor, motivo, `correlation_id` y timestamp.
- La auditoría es inmutable (solo INSERT) y permite reconstruir punta a punta hasta el evento publicado.
- No se registran datos sensibles en la auditoría.

## 6. Cumplimiento y confidencialidad de la prueba

- Caso ficticio: no se usan datos reales ni documentación confidencial de empleadores.
- El diseño asume PCI-DSS: la plataforma no almacena PAN; los datos de tarjeta se referencian tokenizados.
