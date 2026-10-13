package com.prisma.nominations.worker.service;

import com.prisma.nominations.common.correlation.CorrelationContext;
import com.prisma.nominations.common.domain.NominationResult;
import com.prisma.nominations.common.domain.NominationStatus;
import com.prisma.nominations.persistence.domain.Nomination;
import com.prisma.nominations.persistence.domain.NominationStatusHistory;
import com.prisma.nominations.persistence.repository.NominationRepository;
import com.prisma.nominations.persistence.repository.NominationStatusHistoryRepository;
import com.prisma.nominations.worker.config.AbmProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Aplica los desenlaces del envio a ABM y decide reintentos vs estados terminales.
 * Cada metodo se ejecuta en su propia transaccion y, cuando corresponde un estado terminal,
 * escribe el evento de resultado en el outbox dentro de la MISMA transaccion (patron outbox).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NominationOutcomeService {

    private final NominationRepository nominationRepository;
    private final NominationStatusHistoryRepository historyRepository;
    private final OutboxWriter outboxWriter;
    private final AbmProperties properties;
    private final MeterRegistry meterRegistry;

    @Transactional
    public void applyAccepted(UUID nominationId, String externalRef) {
        nominationRepository.findById(nominationId).ifPresent(nomination -> {
            if (nomination.getStatus().isTerminal()) {
                return;
            }
            nomination.setExternalRef(externalRef);
            nomination.setUpdatedAt(Instant.now());
            nominationRepository.save(nomination);
            log.info("ABM acepto la nominacion id={} externalRef={}", nominationId, externalRef);
        });
    }

    /** Rechazo funcional definitivo en el envio (4xx de ABM). No se reintenta. */
    @Transactional
    public void applyFunctionalRejection(UUID nominationId, String reasonCode) {
        transitionToTerminal(nominationId, NominationStatus.REJECTED, NominationResult.REJECTED,
                reasonCode, "Rechazo funcional informado por ABM", "ABM");
        meterRegistry.counter("nominations.rejected").increment();
    }

    /** Falla tecnica al enviar. Reintenta con backoff hasta agotar intentos; luego FAILED. */
    @Transactional
    public void applyTechnicalFailure(UUID nominationId, String reason) {
        Optional<Nomination> found = nominationRepository.findById(nominationId);
        if (found.isEmpty()) {
            return;
        }
        Nomination nomination = found.get();
        if (nomination.getStatus().isTerminal()) {
            return;
        }
        if (nomination.getAttempts() < properties.getMaxAttempts()) {
            scheduleRetry(nomination, "Reintento por falla tecnica: " + reason);
            meterRegistry.counter("nominations.retries").increment();
        } else {
            transitionToTerminal(nomination, NominationStatus.FAILED, NominationResult.FAILED,
                    "UPSTREAM_ERROR", "Fallas tecnicas agotaron los reintentos: " + reason, "WORKER");
            meterRegistry.counter("nominations.failed").increment();
        }
    }

    /** ABM no respondio dentro de la ventana. Reintenta limitadamente; luego TIMEOUT. */
    @Transactional
    public void applyTimeout(UUID nominationId) {
        Optional<Nomination> found = nominationRepository.findById(nominationId);
        if (found.isEmpty()) {
            return;
        }
        Nomination nomination = found.get();
        if (nomination.getStatus() != NominationStatus.SENT_TO_ABM) {
            return;
        }
        if (nomination.getAttempts() < properties.getMaxAttempts()) {
            scheduleRetry(nomination, "ABM sin respuesta; se reprograma el envio");
            meterRegistry.counter("nominations.timeouts.retried").increment();
        } else {
            transitionToTerminal(nomination, NominationStatus.TIMEOUT, NominationResult.TIMEOUT,
                    "UPSTREAM_TIMEOUT", "ABM no respondio tras agotar los intentos", "WORKER");
            meterRegistry.counter("nominations.timedout").increment();
        }
    }

    private void scheduleRetry(Nomination nomination, String message) {
        Instant now = Instant.now();
        Instant next = now.plus(backoff(nomination.getAttempts()));
        NominationStatus from = nomination.getStatus();
        nomination.setStatus(NominationStatus.RECEIVED);
        nomination.setNextAttemptAt(next);
        nomination.setUpdatedAt(now);
        nominationRepository.save(nomination);
        recordHistory(nomination, from, NominationStatus.RECEIVED, null, message, "WORKER");
        log.warn("Nominacion id={} reprogramada para reintento en {}", nomination.getId(), next);
    }

    private void transitionToTerminal(UUID nominationId, NominationStatus status, NominationResult result,
                                      String reasonCode, String message, String actor) {
        nominationRepository.findById(nominationId).ifPresent(nomination -> {
            if (nomination.getStatus().isTerminal()) {
                return;
            }
            transitionToTerminal(nomination, status, result, reasonCode, message, actor);
        });
    }

    private void transitionToTerminal(Nomination nomination, NominationStatus status, NominationResult result,
                                      String reasonCode, String message, String actor) {
        Instant now = Instant.now();
        NominationStatus from = nomination.getStatus();
        nomination.setStatus(status);
        nomination.setResult(result);
        nomination.setReasonCode(reasonCode);
        nomination.setReasonMessage(message);
        nomination.setCompletedAt(now);
        nomination.setUpdatedAt(now);
        nomination.setNextAttemptAt(null);
        nominationRepository.save(nomination);

        recordHistory(nomination, from, status, reasonCode, message, actor);
        outboxWriter.writeResult(nomination);
        log.info("Nominacion id={} finalizada status={} reason={}", nomination.getId(), status, reasonCode);
    }

    private Duration backoff(int attempts) {
        long base = properties.getBackoffBase().toMillis();
        long exponential = base * (1L << Math.min(attempts, 10));
        long jitter = ThreadLocalRandom.current().nextLong(0, base + 1);
        return Duration.ofMillis(exponential + jitter);
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
        history.setCorrelationId(CorrelationContext.getCorrelationId());
        history.setOccurredAt(Instant.now());
        historyRepository.save(history);
    }
}
