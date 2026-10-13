package com.prisma.nominations.publisher.service;

import com.prisma.nominations.common.domain.OutboxStatus;
import com.prisma.nominations.persistence.domain.OutboxEvent;
import com.prisma.nominations.persistence.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Transiciones de estado del outbox en transacciones cortas. El reclamo usa version optimista
 * para garantizar que una sola instancia publique cada evento (entrega "exactly once" efectiva
 * sobre el efecto de negocio, con el eventId como clave de deduplicacion en consumidores).
 */
@Service
@RequiredArgsConstructor
public class OutboxStatusService {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public Optional<OutboxEvent> claim(UUID eventId) {
        Optional<OutboxEvent> found = outboxEventRepository.findById(eventId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        OutboxEvent event = found.get();
        if (event.getStatus() != OutboxStatus.PENDING) {
            return Optional.empty();
        }
        event.setStatus(OutboxStatus.PROCESSING);
        event.setAvailableAt(Instant.now());
        outboxEventRepository.save(event);
        return Optional.of(event);
    }

    @Transactional
    public void markPublished(UUID eventId) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            outboxEventRepository.save(event);
        });
    }

    @Transactional
    public FailureAction markFailure(UUID eventId, String error, int maxAttempts, Duration backoff) {
        OutboxEvent event = outboxEventRepository.findById(eventId).orElse(null);
        if (event == null) {
            return FailureAction.RETRY_SCHEDULED;
        }
        event.setAttempts(event.getAttempts() + 1);
        event.setLastError(truncate(error));
        if (event.getAttempts() >= maxAttempts) {
            event.setStatus(OutboxStatus.DLQ);
            outboxEventRepository.save(event);
            return FailureAction.DEAD_LETTERED;
        }
        event.setStatus(OutboxStatus.PENDING);
        event.setAvailableAt(Instant.now().plus(backoff));
        outboxEventRepository.save(event);
        return FailureAction.RETRY_SCHEDULED;
    }

    /** Recupera eventos que quedaron en PROCESSING por una caida entre reclamo y publicacion. */
    @Transactional
    public int resetStuck(Duration threshold, int batchSize) {
        List<OutboxEvent> stuck = outboxEventRepository
                .findByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
                        OutboxStatus.PROCESSING, Instant.now().minus(threshold), PageRequest.of(0, batchSize));
        for (OutboxEvent event : stuck) {
            event.setStatus(OutboxStatus.PENDING);
            event.setAvailableAt(Instant.now());
            outboxEventRepository.save(event);
        }
        return stuck.size();
    }

    private String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 500 ? error.substring(0, 500) : error;
    }

    public enum FailureAction {
        RETRY_SCHEDULED,
        DEAD_LETTERED
    }
}
