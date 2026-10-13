package com.prisma.nominations.publisher.config;

import com.prisma.nominations.common.domain.OutboxStatus;
import com.prisma.nominations.persistence.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

/**
 * Indicadores de la salud del outbox: backlog pendiente y tamano de la DLQ.
 */
@Configuration
@RequiredArgsConstructor
public class PublisherMetricsConfig {

    private final MeterRegistry meterRegistry;
    private final OutboxEventRepository outboxEventRepository;

    @PostConstruct
    public void registerGauges() {
        Gauge.builder("outbox.pending", outboxEventRepository,
                        repo -> repo.countByStatus(OutboxStatus.PENDING))
                .description("Eventos de outbox pendientes de publicacion")
                .register(meterRegistry);

        Gauge.builder("outbox.dlq", outboxEventRepository,
                        repo -> repo.countByStatus(OutboxStatus.DLQ))
                .description("Eventos de outbox en dead-letter queue")
                .register(meterRegistry);
    }
}
