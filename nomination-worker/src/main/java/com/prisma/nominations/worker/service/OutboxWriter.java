package com.prisma.nominations.worker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prisma.nominations.common.domain.OutboxStatus;
import com.prisma.nominations.common.event.NominationResultEvent;
import com.prisma.nominations.common.util.Masking;
import com.prisma.nominations.persistence.domain.Nomination;
import com.prisma.nominations.persistence.domain.OutboxEvent;
import com.prisma.nominations.persistence.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Escribe el evento de resultado en la tabla de outbox. DEBE invocarse dentro de la misma
 * transaccion que el cambio de estado: asi el evento existe si y solo si el estado quedo confirmado.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public void writeResult(Nomination nomination) {
        NominationResultEvent event = new NominationResultEvent(
                UUID.randomUUID(),
                NominationResultEvent.TYPE,
                NominationResultEvent.CURRENT_VERSION,
                nomination.getId(),
                nomination.getCustomerId(),
                Masking.mask(nomination.getAccountRef()),
                nomination.getCardMask(),
                nomination.getAlias(),
                nomination.getStatus(),
                nomination.getResult(),
                nomination.getReasonCode(),
                nomination.getReasonMessage(),
                nomination.getAttempts(),
                nomination.getCorrelationId(),
                Instant.now()
        );

        OutboxEvent outbox = new OutboxEvent();
        outbox.setId(event.eventId());
        outbox.setAggregateType("NOMINATION");
        outbox.setAggregateId(nomination.getId());
        outbox.setEventType(event.eventType());
        outbox.setEventVersion(event.eventVersion());
        outbox.setPayload(serialize(event));
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setAttempts(0);
        outbox.setAvailableAt(Instant.now());
        outbox.setCreatedAt(Instant.now());
        outboxEventRepository.save(outbox);

        meterRegistry.counter("nominations.events.enqueued").increment();
        log.info("Evento de resultado encolado. eventId={} nominationId={} status={}",
                event.eventId(), nomination.getId(), nomination.getStatus());
    }

    private String serialize(NominationResultEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento de resultado", e);
        }
    }
}
