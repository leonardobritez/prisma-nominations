package com.prisma.nominations.persistence.domain;

import com.prisma.nominations.common.domain.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Tabla transaccional de outbox. Se escribe en la MISMA transaccion que el cambio de estado,
 * de modo que nunca existe un estado persistido sin su evento (ni un evento sin estado).
 * El event-publisher la lee de forma asincronica y publica el evento al broker.
 */
@Entity
@Table(name = "outbox_event", indexes = {
        @Index(name = "ix_outbox_status_available", columnList = "status, available_at")
})
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

    /** Tambien actua como eventId del evento publicado (clave de deduplicacion en consumidores). */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 32)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    /** Payload JSON del evento. */
    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
