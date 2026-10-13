package com.prisma.nominations.api.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta de consulta de estado actual. Todos los identificadores sensibles van enmascarados.
 */
public record NominationStatusResponse(
        @JsonProperty("nomination_id") UUID nominationId,
        @JsonProperty("request_id") UUID requestId,
        @JsonProperty("customer_id") String customerId,
        @JsonProperty("account_mask") String accountMask,
        @JsonProperty("card_mask") String cardMask,
        @JsonProperty("alias") String alias,
        @JsonProperty("status") String status,
        @JsonProperty("result") String result,
        @JsonProperty("reason_code") String reasonCode,
        @JsonProperty("reason_message") String reasonMessage,
        @JsonProperty("attempts") int attempts,
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("completed_at") Instant completedAt,
        @JsonProperty("history_url") String historyUrl
) {
}
