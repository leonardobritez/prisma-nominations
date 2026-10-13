package com.prisma.nominations.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prisma.nominations.persistence.domain.IdempotencyRecord;
import com.prisma.nominations.persistence.domain.Nomination;
import com.prisma.nominations.persistence.domain.NominationStatusHistory;
import com.prisma.nominations.persistence.repository.IdempotencyRepository;
import com.prisma.nominations.persistence.repository.NominationRepository;
import com.prisma.nominations.persistence.repository.NominationStatusHistoryRepository;
import com.prisma.nominations.api.web.dto.CreateNominationRequest;
import com.prisma.nominations.api.web.dto.NominationAcceptedResponse;
import com.prisma.nominations.api.web.dto.NominationHistoryResponse;
import com.prisma.nominations.api.web.dto.NominationStatusResponse;
import com.prisma.nominations.common.correlation.CorrelationContext;
import com.prisma.nominations.common.domain.NominationStatus;
import com.prisma.nominations.common.util.Masking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de la API. Responsabilidades:
 * <ul>
 *     <li>Validar y persistir la solicitud (estado inicial {@code RECEIVED}).</li>
 *     <li>Aplicar idempotencia de ingreso (evita doble envio a ABM y doble evento).</li>
 *     <li>Exponer consultas de estado actual e historial.</li>
 * </ul>
 * No llama a ABM ni publica eventos: esas fronteras pertenecen al worker y al publisher.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NominationService {

    public static final String IDEMPOTENT_REPLAY_MESSAGE =
            "Solicitud ya registrada previamente. Se devuelve la respuesta original (idempotencia).";

    private final NominationRepository nominationRepository;
    private final NominationStatusHistoryRepository historyRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.idempotency.ttl-hours:24}")
    private long idempotencyTtlHours;

    /**
     * Crea (o reproduce) una nominacion de forma idempotente.
     *
     * @return el resultado de la operacion con la bandera {@code replayed}.
     */
    @Transactional
    public CreationResult create(CreateNominationRequest request, String idempotencyKey) {
        String key = resolveKey(idempotencyKey, request);
        String requestHash = Masking.sha256(canonical(request));
        String correlationId = CorrelationContext.getOrCreateCorrelationId();

        Optional<IdempotencyRecord> existing = idempotencyRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash);
        }

        Nomination nomination = persistNomination(request, correlationId);

        NominationAcceptedResponse response = new NominationAcceptedResponse(
                nomination.getId(),
                nomination.getRequestId(),
                nomination.getStatus().name(),
                nomination.getCreatedAt(),
                statusUrl(nomination.getId()),
                "Solicitud aceptada para procesamiento asincronico."
        );

        try {
            storeIdempotency(key, requestHash, nomination.getId(), response);
        } catch (DataIntegrityViolationException race) {
            // Otra instancia concurrente gano la carrera por la misma clave: se reproduce su resultado.
            log.info("Carrera de idempotencia detectada para key={}. Se reproduce resultado existente.", key);
            return idempotencyRepository.findByIdempotencyKey(key)
                    .map(record -> replay(record, requestHash))
                    .orElseGet(() -> CreationResult.created(response));
        }

        return CreationResult.created(response);
    }

    @Transactional(readOnly = true)
    public NominationStatusResponse getStatus(UUID nominationId) {
        Nomination nomination = nominationRepository.findById(nominationId).orElse(null);
        if (nomination == null) {
            return null;
        }
        return toStatusResponse(nomination);
    }

    @Transactional(readOnly = true)
    public NominationHistoryResponse getHistory(UUID nominationId) {
        Nomination nomination = nominationRepository.findById(nominationId).orElse(null);
        if (nomination == null) {
            return null;
        }
        List<NominationHistoryResponse.HistoryEntry> entries =
                historyRepository.findByNominationIdOrderByOccurredAtAsc(nominationId).stream()
                        .map(this::toHistoryEntry)
                        .toList();
        return new NominationHistoryResponse(nominationId, nomination.getCorrelationId(), entries);
    }

    private Nomination persistNomination(CreateNominationRequest request, String correlationId) {
        Instant now = Instant.now();
        Nomination nomination = new Nomination();
        nomination.setId(UUID.randomUUID());
        nomination.setCustomerId(request.customerId());
        nomination.setAccountRef(request.accountId());
        nomination.setCardToken(request.cardId());
        nomination.setCardMask(Masking.mask(request.cardId()));
        nomination.setAlias(request.alias());
        nomination.setRequestId(request.requestId());
        nomination.setStatus(NominationStatus.RECEIVED);
        nomination.setAttempts(0);
        nomination.setNextAttemptAt(now);
        nomination.setCorrelationId(correlationId);
        nomination.setCreatedAt(now);
        nomination.setUpdatedAt(now);
        nominationRepository.save(nomination);

        historyRepository.save(history(nomination.getId(), null, NominationStatus.RECEIVED,
                null, "Solicitud recibida y validada", "API", correlationId, now));

        log.info("Nominacion creada id={} correlationId={} status=RECEIVED",
                nomination.getId(), correlationId);
        return nomination;
    }

    private NominationStatusHistory history(UUID nominationId, NominationStatus from, NominationStatus to,
                                            String reasonCode, String message, String actor,
                                            String correlationId, Instant occurredAt) {
        NominationStatusHistory entry = new NominationStatusHistory();
        entry.setId(UUID.randomUUID());
        entry.setNominationId(nominationId);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setReasonCode(reasonCode);
        entry.setMessage(message);
        entry.setActor(actor);
        entry.setCorrelationId(correlationId);
        entry.setOccurredAt(occurredAt);
        return entry;
    }

    private void storeIdempotency(String key, String requestHash, UUID nominationId,
                                  NominationAcceptedResponse response) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setId(UUID.randomUUID());
        record.setIdempotencyKey(key);
        record.setRequestHash(requestHash);
        record.setNominationId(nominationId);
        record.setResponseStatus(202);
        record.setResponseBody(serialize(response));
        record.setCreatedAt(Instant.now());
        record.setExpiresAt(Instant.now().plus(Duration.ofHours(idempotencyTtlHours)));
        idempotencyRepository.save(record);
    }

    private CreationResult replay(IdempotencyRecord record, String requestHash) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(
                    "La clave de idempotencia ya fue usada con un cuerpo distinto.");
        }
        NominationAcceptedResponse response = deserialize(record.getResponseBody());
        return CreationResult.replayed(response);
    }

    private String resolveKey(String idempotencyKey, CreateNominationRequest request) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyKey.trim();
        }
        return request.requestId().toString();
    }

    private String canonical(CreateNominationRequest request) {
        return request.customerId() + "|" + request.accountId() + "|" + request.cardId()
                + "|" + request.alias() + "|" + request.requestId();
    }

    private String serialize(NominationAcceptedResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la respuesta idempotente", e);
        }
    }

    private NominationAcceptedResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, NominationAcceptedResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo deserializar la respuesta idempotente", e);
        }
    }

    private NominationStatusResponse toStatusResponse(Nomination n) {
        return new NominationStatusResponse(
                n.getId(),
                n.getRequestId(),
                n.getCustomerId(),
                Masking.mask(n.getAccountRef()),
                n.getCardMask(),
                n.getAlias(),
                n.getStatus().name(),
                n.getResult() == null ? null : n.getResult().name(),
                n.getReasonCode(),
                n.getReasonMessage(),
                n.getAttempts(),
                n.getCorrelationId(),
                n.getCreatedAt(),
                n.getUpdatedAt(),
                n.getCompletedAt(),
                statusUrl(n.getId()) + "/history"
        );
    }

    private NominationHistoryResponse.HistoryEntry toHistoryEntry(NominationStatusHistory h) {
        return new NominationHistoryResponse.HistoryEntry(
                h.getFromStatus() == null ? null : h.getFromStatus().name(),
                h.getToStatus().name(),
                h.getReasonCode(),
                h.getMessage(),
                h.getActor(),
                h.getOccurredAt()
        );
    }

    private String statusUrl(UUID nominationId) {
        return "/v1/nominations/" + nominationId;
    }

    /** Resultado de la operacion de creacion, distinguiendo creacion real de replay idempotente. */
    public record CreationResult(NominationAcceptedResponse response, boolean replayed) {
        static CreationResult created(NominationAcceptedResponse response) {
            return new CreationResult(response, false);
        }

        static CreationResult replayed(NominationAcceptedResponse response) {
            return new CreationResult(response, true);
        }
    }
}
