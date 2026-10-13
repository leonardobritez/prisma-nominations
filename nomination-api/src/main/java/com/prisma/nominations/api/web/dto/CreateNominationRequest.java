package com.prisma.nominations.api.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Contrato de entrada de {@code POST /v1/nominations}.
 * Los nombres se exponen en snake_case para preservar el contrato de referencia.
 */
public record CreateNominationRequest(

        @JsonProperty("customer_id")
        @NotBlank(message = "customer_id es obligatorio")
        @Size(max = 64, message = "customer_id excede la longitud maxima")
        String customerId,

        @JsonProperty("account_id")
        @NotBlank(message = "account_id es obligatorio")
        @Size(max = 128, message = "account_id excede la longitud maxima")
        String accountId,

        @JsonProperty("card_id")
        @NotBlank(message = "card_id es obligatorio")
        @Size(max = 256, message = "card_id excede la longitud maxima")
        String cardId,

        @JsonProperty("alias")
        @NotBlank(message = "alias es obligatorio")
        @Size(max = 32, message = "alias excede la longitud maxima")
        @Pattern(regexp = "^[A-Z0-9_]+$", message = "alias solo admite A-Z, 0-9 y _")
        String alias,

        @JsonProperty("request_id")
        @NotNull(message = "request_id es obligatorio")
        UUID requestId
) {
}
