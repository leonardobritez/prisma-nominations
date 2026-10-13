package com.prisma.nominations.api.web;

import com.prisma.nominations.api.service.NominationService;
import com.prisma.nominations.api.web.dto.CreateNominationRequest;
import com.prisma.nominations.api.web.dto.NominationAcceptedResponse;
import com.prisma.nominations.api.web.dto.NominationHistoryResponse;
import com.prisma.nominations.api.web.dto.NominationStatusResponse;
import com.prisma.nominations.common.correlation.Correlation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

/**
 * Borde sincrono de la plataforma. Acepta la solicitud y delega el procesamiento asincronico.
 * El consumidor nunca queda bloqueado esperando la respuesta de ABM.
 */
@RestController
@RequestMapping("/v1/nominations")
@RequiredArgsConstructor
@Tag(name = "Nominations", description = "Nominacion de cuentas y tarjetas")
public class NominationController {

    private final NominationService nominationService;

    @PostMapping
    @Operation(summary = "Acepta una solicitud de nominacion (asincronica)")
    public ResponseEntity<NominationAcceptedResponse> create(
            @Valid @RequestBody CreateNominationRequest request,
            @RequestHeader(value = Correlation.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {

        NominationService.CreationResult result = nominationService.create(request, idempotencyKey);
        String location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(result.response().nominationId())
                .toUriString();

        return ResponseEntity.accepted()
                .header(HttpHeaders.LOCATION, location)
                .header(Correlation.REQUEST_ID_HEADER, request.requestId().toString())
                .body(result.response());
    }

    @GetMapping("/{nominationId}")
    @Operation(summary = "Consulta el estado actual de una nominacion")
    public ResponseEntity<NominationStatusResponse> getStatus(@PathVariable UUID nominationId) {
        NominationStatusResponse response = nominationService.getStatus(nominationId);
        return response == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(response);
    }

    @GetMapping("/{nominationId}/history")
    @Operation(summary = "Consulta el historial de cambios de estado (trazabilidad)")
    public ResponseEntity<NominationHistoryResponse> getHistory(@PathVariable UUID nominationId) {
        NominationHistoryResponse response = nominationService.getHistory(nominationId);
        return response == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(response);
    }
}
