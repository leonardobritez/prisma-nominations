package com.prisma.nominations.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prisma.nominations.common.domain.NominationResult;
import com.prisma.nominations.common.domain.NominationStatus;
import com.prisma.nominations.common.domain.OutboxStatus;
import com.prisma.nominations.common.event.NominationResultEvent;
import com.prisma.nominations.persistence.domain.OutboxEvent;
import com.prisma.nominations.persistence.repository.OutboxEventRepository;
import com.prisma.nominations.publisher.publish.EventPublisher;
import com.prisma.nominations.publisher.publish.EventPublicationException;
import com.prisma.nominations.publisher.service.OutboxRelay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OutboxRelayIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private OutboxRelay relay;
    @Autowired
    private StubEventPublisher publisher;

    @BeforeEach
    void clean() {
        outboxEventRepository.deleteAll();
        publisher.reset();
    }

    @Test
    @DisplayName("E8 - Publicacion exitosa: el evento pasa a PUBLISHED una sola vez")
    void e8_successfulPublication() {
        OutboxEvent event = createPendingEvent();

        relay.relay();

        assertThat(reload(event).getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(publisher.published).hasSize(1);
        assertThat(publisher.published.get(0)).contains(event.getId().toString());
    }

    @Test
    @DisplayName("E8b - Fallo persistente: reintentos y luego dead-letter queue")
    void e8b_failureLeadsToDlq() throws InterruptedException {
        OutboxEvent event = createPendingEvent();
        publisher.failing = true;

        relay.relay();
        Thread.sleep(10);
        relay.relay();
        Thread.sleep(10);
        relay.relay();

        assertThat(reload(event).getStatus()).isEqualTo(OutboxStatus.DLQ);
        assertThat(publisher.deadLettered).hasSize(1);
        assertThat(publisher.published).isEmpty();
    }

    @Test
    @DisplayName("E9 - Consumidor fuera de servicio: sin perdida, se publica al recuperarse")
    void e9_consumerRecoversWithoutLoss() throws InterruptedException {
        OutboxEvent event = createPendingEvent();

        publisher.failing = true;
        relay.relay();
        Thread.sleep(10);
        relay.relay();
        assertThat(reload(event).getStatus()).isEqualTo(OutboxStatus.PENDING);

        publisher.failing = false;
        Thread.sleep(10);
        relay.relay();

        assertThat(reload(event).getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(publisher.published).hasSize(1);
    }

    private OutboxEvent createPendingEvent() {
        NominationResultEvent event = new NominationResultEvent(
                UUID.randomUUID(),
                NominationResultEvent.TYPE,
                NominationResultEvent.CURRENT_VERSION,
                UUID.randomUUID(),
                "123456",
                "****7654",
                "****ENCE",
                "CUENTA_PRINCIPAL",
                NominationStatus.APPROVED,
                NominationResult.APPROVED,
                null,
                null,
                1,
                UUID.randomUUID().toString(),
                Instant.now());

        OutboxEvent outbox = new OutboxEvent();
        outbox.setId(event.eventId());
        outbox.setAggregateType("NOMINATION");
        outbox.setAggregateId(event.nominationId());
        outbox.setEventType(event.eventType());
        outbox.setEventVersion(event.eventVersion());
        outbox.setPayload(writeJson(event));
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setAttempts(0);
        outbox.setAvailableAt(Instant.now().minusSeconds(1));
        outbox.setCreatedAt(Instant.now());
        return outboxEventRepository.save(outbox);
    }

    private String writeJson(NominationResultEvent event) {
        try {
            return new ObjectMapper().findAndRegisterModules().writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private OutboxEvent reload(OutboxEvent event) {
        return outboxEventRepository.findById(event.getId()).orElseThrow();
    }

    @TestConfiguration
    static class StubConfig {
        @Bean
        @Primary
        StubEventPublisher stubEventPublisher() {
            return new StubEventPublisher();
        }
    }

    static class StubEventPublisher implements EventPublisher {
        volatile boolean failing;
        final List<String> published = new CopyOnWriteArrayList<>();
        final List<String> deadLettered = new CopyOnWriteArrayList<>();

        @Override
        public void publish(String key, String payload) {
            if (failing) {
                throw new EventPublicationException("broker no disponible (simulado)", null);
            }
            published.add(key + "|" + payload);
        }

        @Override
        public void publishDeadLetter(String key, String payload, String error) {
            deadLettered.add(key + "|" + error);
        }

        void reset() {
            failing = false;
            published.clear();
            deadLettered.clear();
        }
    }
}
