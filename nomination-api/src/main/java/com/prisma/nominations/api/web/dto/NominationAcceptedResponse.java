package com.prisma.nominations.api.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta 202 Accepted. Devuelve el identificador de la nominacion y la URL para consultar estado,
 * sin exponer ningun dato sensible.
 */
public record NominationAcceptedResponse(
        @JsonProperty("nomination_id") UUID nominationId,
        @JsonProperty("request_id") UUID requestId,
        @JsonProperty("status") String status,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("status_url") String statusUrl,
        @JsonProperty("message") String message
) {
}
