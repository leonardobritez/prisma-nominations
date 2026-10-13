package com.prisma.nominations.worker.config;

import com.prisma.nominations.common.domain.NominationStatus;
import com.prisma.nominations.persistence.repository.NominationRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

/**
 * Indicadores de "profundidad de backlog" que permiten detectar degradacion antes de un incidente.
 */
@Configuration
@RequiredArgsConstructor
public class WorkerMetricsConfig {

    private final MeterRegistry meterRegistry;
    private final NominationRepository nominationRepository;

    @PostConstruct
    public void registerGauges() {
        Gauge.builder("nominations.pending", nominationRepository,
                        repo -> repo.countByStatus(NominationStatus.RECEIVED))
                .description("Nominaciones pendientes de envio a ABM")
                .register(meterRegistry);

        Gauge.builder("nominations.inflight", nominationRepository,
                        repo -> repo.countByStatus(NominationStatus.SENT_TO_ABM))
                .description("Nominaciones enviadas a ABM y sin resultado")
                .register(meterRegistry);
    }
}
