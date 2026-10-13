package com.prisma.nominations.persistence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de idempotencia de ingreso. La clave viene del header {@code Idempotency-Key}
 * (o del {@code request_id} del cuerpo como alternativa). Se conserva una ventana configurable
 * (por defecto 24h) y se guarda una huella del payload para detectar reuso con cuerpo distinto.
 */
@Entity
@Table(name = "idempotency_record", indexes = {
        @Index(name = "ux_idempotency_key", columnList = "idempotency_key", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    /** SHA-256 del cuerpo canonico. */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "nomination_id", nullable = false)
    private UUID nominationId;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body", nullable = false, columnDefinition = "text")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
