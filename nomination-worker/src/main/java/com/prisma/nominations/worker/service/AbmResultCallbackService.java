package com.prisma.nominations.worker.service;

import com.prisma.nominations.common.correlation.CorrelationContext;
import com.prisma.nominations.common.domain.NominationResult;
import com.prisma.nominations.common.domain.NominationStatus;
import com.prisma.nominations.common.domain.ReasonCode;
import com.prisma.nominations.persistence.domain.Nomination;
import com.prisma.nominations.persistence.domain.NominationStatusHistory;
import com.prisma.nominations.persistence.repository.NominationRepository;
import com.prisma.nominations.persistence.repository.NominationStatusHistoryRepository;
import com.prisma.nominations.worker.abm.AbmResultCallback;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Procesa el resultado asincronico de ABM de forma idempotente.
 *
 * <p>Si la nominacion ya alcanzo un estado terminal, el callback se considera duplicado y se
 * ignora (no cambia estado ni publica un segundo evento). Esto cubre E7 (respuesta duplicada) y
 * la reentrega de mensajes.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AbmResultCallbackService {

    private final NominationRepository nominationRepository;
    private final NominationStatusHistoryRepository historyRepository;
    private final OutboxWriter outboxWriter;
    private final MeterRegistry meterRegistry;

    @Transactional
    public Outcome process(AbmResultCallback callback) {
        Nomination nomination = nominationRepository.findById(callback.nominationId()).orElse(null);
        if (nomination == null) {
            log.warn("Callback de ABM para nominacion desconocida id={}", callback.nominationId());
            meterRegistry.counter("nominations.callback.unknown").increment();
            return Outcome.UNKNOWN;
        }
        if (nomination.getStatus().isTerminal()) {
            log.info("Callback duplicado ignorado. nominationId={} status={}",
                    nomination.getId(), nomination.getStatus());
            meterRegistry.counter("nominations.callback.duplicate").increment();
            return Outcome.DUPLICATE_IGNORED;
        }

        NominationStatus target = map(callback.result());
        String reasonCode = resolveReasonCode(callback.result(), callback.rawReason());
        String reasonMessage = resolveReasonMessage(callback.result(), callback.rawReason());

        Instant now = Instant.now();
        NominationStatus from = nomination.getStatus();
        nomination.setStatus(target);
        nomination.setResult(callback.result());
        nomination.setExternalRef(callback.externalRef());
        nomination.setReasonCode(reasonCode);
        nomination.setReasonMessage(reasonMessage);
        nomination.setCompletedAt(now);
        nomination.setUpdatedAt(now);
        nomination.setNextAttemptAt(null);
        nominationRepository.save(nomination);

        recordHistory(nomination, from, target, reasonCode, reasonMessage, "ABM");
        outboxWriter.writeResult(nomination);

        meterRegistry.counter("nominations.completed", "result", callback.result().name()).increment();
        log.info("Resultado de ABM aplicado. nominationId={} status={} reason={}",
                nomination.getId(), target, reasonCode);
        return Outcome.APPLIED;
    }

    private NominationStatus map(NominationResult result) {
        return switch (result) {
            case APPROVED -> NominationStatus.APPROVED;
            case REJECTED -> NominationStatus.REJECTED;
            case FAILED -> NominationStatus.FAILED;
            case TIMEOUT -> NominationStatus.TIMEOUT;
        };
    }

    private String resolveReasonCode(NominationResult result, String rawReason) {
        return switch (result) {
            case APPROVED -> null;
            case REJECTED -> ReasonCode.fromExternal(rawReason).name();
            case FAILED -> ReasonCode.UPSTREAM_ERROR.name();
            case TIMEOUT -> ReasonCode.UPSTREAM_TIMEOUT.name();
        };
    }

    private String resolveReasonMessage(NominationResult result, String rawReason) {
        return switch (result) {
            case APPROVED -> null;
            case REJECTED -> "Rechazo funcional: " + ReasonCode.fromExternal(rawReason).name();
            case FAILED -> "ABM informo una falla tecnica";
            case TIMEOUT -> "ABM no respondio en el tiempo esperado";
        };
    }

    private void recordHistory(Nomination nomination, NominationStatus from, NominationStatus to,
                               String reasonCode, String message, String actor) {
        NominationStatusHistory history = new NominationStatusHistory();
        history.setId(UUID.randomUUID());
        history.setNominationId(nomination.getId());
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setReasonCode(reasonCode);
        history.setMessage(message);
        history.setActor(actor);
        history.setCorrelationId(CorrelationContext.getCorrelationId() != null
                ? CorrelationContext.getCorrelationId() : nomination.getCorrelationId());
        history.setOccurredAt(Instant.now());
        historyRepository.save(history);
    }

    public enum Outcome {
        APPLIED,
        DUPLICATE_IGNORED,
        UNKNOWN
    }
}
