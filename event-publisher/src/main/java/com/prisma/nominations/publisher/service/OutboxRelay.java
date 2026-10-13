package com.prisma.nominations.publisher.service;

import com.prisma.nominations.common.domain.OutboxStatus;
import com.prisma.nominations.persistence.domain.OutboxEvent;
import com.prisma.nominations.persistence.repository.OutboxEventRepository;
import com.prisma.nominations.publisher.publish.EventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Relay del outbox: reclama eventos pendientes, los publica y confirma. Ante fallos aplica
 * reintentos con backoff y, al agotar intentos, los envia a la dead-letter queue.
 * Desacopla completamente la publicacion de la transaccion de negocio.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxStatusService statusService;
    private final EventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    @Value("${app.publisher.batch-size:50}")
    private int batchSize;

    @Value("${app.publisher.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.publisher.backoff:5s}")
    private Duration backoff;

    @Value("${app.publisher.stuck-threshold:60s}")
    private Duration stuckThreshold;

    @Scheduled(fixedDelayString = "${app.publisher.interval-ms:1000}")
    public void relay() {
        List<OutboxEvent> batch = outboxEventRepository
                .findByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
                        OutboxStatus.PENDING, Instant.now(), PageRequest.of(0, batchSize));

        for (OutboxEvent candidate : batch) {
            Optional<OutboxEvent> claimed = statusService.claim(candidate.getId());
            if (claimed.isEmpty()) {
                continue;
            }
            OutboxEvent event = claimed.get();
            String key = event.getAggregateId().toString();
            try {
                eventPublisher.publish(key, event.getPayload());
                statusService.markPublished(event.getId());
                meterRegistry.counter("outbox.published").increment();
                log.info("Evento publicado. eventId={} nominationId={} eventType={}",
                        event.getId(), key, event.getEventType());
            } catch (Exception e) {
                OutboxStatusService.FailureAction action =
                        statusService.markFailure(event.getId(), e.getMessage(), maxAttempts, backoff);
                if (action == OutboxStatusService.FailureAction.DEAD_LETTERED) {
                    eventPublisher.publishDeadLetter(key, event.getPayload(), e.getMessage());
                    meterRegistry.counter("outbox.deadlettered").increment();
                    log.error("Evento enviado a DLQ. eventId={} nominationId={} error={}",
                            event.getId(), key, e.getMessage());
                } else {
                    meterRegistry.counter("outbox.retried").increment();
                    log.warn("Publicacion fallida, se reintentara. eventId={} error={}",
                            event.getId(), e.getMessage());
                }
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.publisher.recovery-interval-ms:10000}")
    public void recoverStuck() {
        int reset = statusService.resetStuck(stuckThreshold, batchSize);
        if (reset > 0) {
            meterRegistry.counter("outbox.recovered").increment(reset);
            log.warn("Se reencolaron {} eventos que estaban en PROCESSING", reset);
        }
    }
}
