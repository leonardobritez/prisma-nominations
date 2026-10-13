package com.prisma.nominations.persistence.domain;

import com.prisma.nominations.common.domain.NominationResult;
import com.prisma.nominations.common.domain.NominationStatus;
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
 * Agregado raiz de la nominacion. Persistido por la API (estado inicial) y actualizado por el worker
 * (transiciones de estado). No almacena PAN ni datos sensibles completos: la tarjeta se guarda
 * como token y referencia enmascarada.
 */
@Entity
@Table(name = "nomination", indexes = {
        @Index(name = "ix_nomination_status", columnList = "status"),
        @Index(name = "ix_nomination_request_id", columnList = "request_id"),
        @Index(name = "ix_nomination_correlation_id", columnList = "correlation_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Nomination {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    /** Referencia opaca de cuenta usada para integrar con ABM. Nunca se expone completa. */
    @Column(name = "account_ref", nullable = false, length = 128)
    private String accountRef;

    /** Token de tarjeta emitido por el proveedor. Nunca es un PAN. */
    @Column(name = "card_token", nullable = false, length = 256)
    private String cardToken;

    @Column(name = "card_mask", nullable = false, length = 16)
    private String cardMask;

    @Column(name = "alias", nullable = false, length = 32)
    private String alias;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private NominationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 24)
    private NominationResult result;

    /** Referencia externa devuelta por ABM al aceptar el envio. */
    @Column(name = "external_ref", length = 128)
    private String externalRef;

    @Column(name = "reason_code", length = 48)
    private String reasonCode;

    @Column(name = "reason_message", length = 256)
    private String reasonMessage;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    /** Proximo instante habilitado para reintentar el envio a ABM (backoff). */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sent_to_abm_at")
    private Instant sentToAbmAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
