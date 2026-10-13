package com.prisma.nominations.worker.web;

import com.prisma.nominations.worker.service.AbmResultCallbackService;
import com.prisma.nominations.worker.abm.AbmResultCallback;
import com.prisma.nominations.common.domain.NominationResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Endpoint interno por el que ABM entrega el resultado asincronico. Es idempotente: acepta
 * reentregas sin duplicar estado ni eventos. En produccion se protege con mTLS y firma;
 * aqui se valida un token compartido si esta configurado.
 */
@RestController
@RequestMapping("/internal/v1/abm")
@RequiredArgsConstructor
public class AbmCallbackController {

    private final AbmResultCallbackService callbackService;

    @Value("${app.abm.callback-token:}")
    private String callbackToken;

    @PostMapping("/callbacks")
    public ResponseEntity<Map<String, Object>> receive(
            @RequestBody AbmCallbackRequest request,
            @RequestHeader(value = "X-Callback-Token", required = false) String token) {

        if (callbackToken != null && !callbackToken.isBlank() && !callbackToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "token de callback invalido"));
        }

        AbmResultCallback callback = new AbmResultCallback(
                request.nominationId(),
                request.externalRef(),
                request.result(),
                request.reason(),
                "ABM_CALLBACK");

        AbmResultCallbackService.Outcome outcome = callbackService.process(callback);
        return ResponseEntity.ok(Map.of(
                "nomination_id", request.nominationId().toString(),
                "outcome", outcome.name()));
    }

    public record AbmCallbackRequest(
            @JsonProperty("nomination_id") UUID nominationId,
            @JsonProperty("external_ref") String externalRef,
            @JsonProperty("result") NominationResult result,
            @JsonProperty("reason") String reason
    ) {
    }
}
