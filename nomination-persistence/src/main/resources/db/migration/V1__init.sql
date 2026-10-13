-- Esquema inicial del dominio de nominaciones (PostgreSQL).
-- El esquema se versiona y se aplica una sola vez por Flyway; los servicios usan ddl-auto=none.

CREATE TABLE nomination (
    id                 UUID         NOT NULL,
    customer_id        VARCHAR(64)  NOT NULL,
    account_ref        VARCHAR(128) NOT NULL,
    card_token         VARCHAR(256) NOT NULL,
    card_mask          VARCHAR(16)  NOT NULL,
    alias              VARCHAR(32)  NOT NULL,
    request_id         UUID         NOT NULL,
    status             VARCHAR(24)  NOT NULL,
    result             VARCHAR(24),
    external_ref       VARCHAR(128),
    reason_code        VARCHAR(48),
    reason_message     VARCHAR(256),
    attempts           INTEGER      NOT NULL,
    next_attempt_at    TIMESTAMP WITH TIME ZONE,
    correlation_id     VARCHAR(64),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_to_abm_at     TIMESTAMP WITH TIME ZONE,
    completed_at       TIMESTAMP WITH TIME ZONE,
    version            BIGINT       NOT NULL,
    CONSTRAINT pk_nomination PRIMARY KEY (id)
);

CREATE INDEX ix_nomination_status ON nomination (status);
CREATE INDEX ix_nomination_request_id ON nomination (request_id);
CREATE INDEX ix_nomination_correlation_id ON nomination (correlation_id);
-- Indice de la consulta de polling del worker.
CREATE INDEX ix_nomination_pending ON nomination (status, next_attempt_at);

CREATE TABLE nomination_status_history (
    id              UUID        NOT NULL,
    nomination_id   UUID        NOT NULL,
    from_status     VARCHAR(24),
    to_status       VARCHAR(24) NOT NULL,
    reason_code     VARCHAR(48),
    message         VARCHAR(256),
    actor           VARCHAR(24) NOT NULL,
    correlation_id  VARCHAR(64),
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_nomination_status_history PRIMARY KEY (id)
);

CREATE INDEX ix_history_nomination ON nomination_status_history (nomination_id, occurred_at);

CREATE TABLE idempotency_record (
    id                UUID         NOT NULL,
    idempotency_key   VARCHAR(128) NOT NULL,
    request_hash      VARCHAR(64)  NOT NULL,
    nomination_id     UUID         NOT NULL,
    response_status   INTEGER      NOT NULL,
    response_body     TEXT         NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_idempotency_record PRIMARY KEY (id),
    CONSTRAINT ux_idempotency_key UNIQUE (idempotency_key)
);

CREATE TABLE outbox_event (
    id              UUID        NOT NULL,
    aggregate_type  VARCHAR(32) NOT NULL,
    aggregate_id    UUID        NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    event_version   INTEGER     NOT NULL,
    payload         TEXT        NOT NULL,
    status          VARCHAR(16) NOT NULL,
    attempts        INTEGER     NOT NULL,
    available_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error      VARCHAR(512),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at    TIMESTAMP WITH TIME ZONE,
    version         BIGINT      NOT NULL,
    CONSTRAINT pk_outbox_event PRIMARY KEY (id)
);

-- Indice del polling del event-publisher.
CREATE INDEX ix_outbox_status_available ON outbox_event (status, available_at);
