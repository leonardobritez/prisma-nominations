package com.prisma.nominations.api.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de consulta de historial (trazabilidad de cambios de estado).
 */
public record NominationHistoryResponse(
        @JsonProperty("nomination_id") UUID nominationId,
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("entries") List<HistoryEntry> entries
) {
    public record HistoryEntry(
            @JsonProperty("from_status") String fromStatus,
            @JsonProperty("to_status") String toStatus,
            @JsonProperty("reason_code") String reasonCode,
            @JsonProperty("message") String message,
            @JsonProperty("actor") String actor,
            @JsonProperty("occurred_at") Instant occurredAt
    ) {
    }
}
