package com.prisma.nominations.persistence.domain;

import com.prisma.nominations.common.domain.NominationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Historial de cambios de estado (auditoria inmutable). Solo se insertan registros, nunca se
 * actualizan ni eliminan. Permite reconstruir la trazabilidad end-to-end.
 */
@Entity
@Table(name = "nomination_status_history", indexes = {
        @Index(name = "ix_history_nomination", columnList = "nomination_id, occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
public class NominationStatusHistory {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "nomination_id", nullable = false, updatable = false)
    private UUID nominationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 24)
    private NominationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 24)
    private NominationStatus toStatus;

    @Column(name = "reason_code", length = 48)
    private String reasonCode;

    @Column(name = "message", length = 256)
    private String message;

    /** Actor responsable de la transicion: API, WORKER, ABM, SYSTEM. */
    @Column(name = "actor", nullable = false, length = 24)
    private String actor;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
