package com.prisma.nominations.worker.abm.mock;

import com.prisma.nominations.common.domain.NominationResult;
import com.prisma.nominations.persistence.domain.Nomination;
import com.prisma.nominations.worker.abm.AbmClient;
import com.prisma.nominations.worker.abm.AbmDispatchResult;
import com.prisma.nominations.worker.abm.AbmResultCallback;
import com.prisma.nominations.worker.config.AbmProperties;
import com.prisma.nominations.worker.service.AbmResultCallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;

/**
 * Simulador de ABM para demostracion y pruebas. Reproduce el comportamiento asincronico real:
 * acepta el envio y, tras un retraso, entrega el resultado por callback (que puede demorar o duplicarse).
 *
 * <p>Se controla por el sufijo de {@code accountRef}:</p>
 * <ul>
 *     <li>{@code ..._REJ}  -> callback con rechazo funcional.</li>
 *     <li>{@code ..._TO}   -> no entrega callback (simula timeout).</li>
 *     <li>{@code ..._DUP}  -> entrega el callback dos veces (simula duplicado).</li>
 *     <li>{@code ..._FAIL} -> falla tecnica recuperable en el primer intento.</li>
 *     <li>resto              -> callback con aprobacion.</li>
 * </ul>
 */
@Component
@Profile("mock-abm")
@RequiredArgsConstructor
@Slf4j
public class MockAbmClient implements AbmClient {

    private final TaskScheduler abmMockScheduler;
    private final AbmResultCallbackService callbackService;
    private final AbmProperties properties;

    @Override
    public AbmDispatchResult dispatch(Nomination nomination) {
        String account = nomination.getAccountRef().toUpperCase(Locale.ROOT);
        String externalRef = "ABM-" + nomination.getId();
        long delay = properties.getMockCallbackDelay().toMillis();

        if (account.endsWith("_FAIL") && nomination.getAttempts() <= 1) {
            throw new com.prisma.nominations.worker.abm.AbmTechnicalException(
                    "Falla tecnica simulada de ABM (reintentable)");
        }
        if (account.endsWith("_TO")) {
            log.info("[MOCK-ABM] Sin respuesta para nominacion id={} (simula timeout)", nomination.getId());
            return AbmDispatchResult.accepted(externalRef);
        }
        if (account.endsWith("_DUP")) {
            schedule(nomination, externalRef, NominationResult.APPROVED, null, delay);
            schedule(nomination, externalRef, NominationResult.APPROVED, null, delay + 50);
            return AbmDispatchResult.accepted(externalRef);
        }
        if (account.endsWith("_REJ")) {
            schedule(nomination, externalRef, NominationResult.REJECTED, "CUSTOMER_BLOCKED", delay);
            return AbmDispatchResult.accepted(externalRef);
        }
        schedule(nomination, externalRef, NominationResult.APPROVED, null, delay);
        return AbmDispatchResult.accepted(externalRef);
    }

    private void schedule(Nomination nomination, String externalRef, NominationResult result,
                          String reason, long delayMillis) {
        AbmResultCallback callback = new AbmResultCallback(
                nomination.getId(), externalRef, result, reason, "MOCK_ABM");
        abmMockScheduler.schedule(
                () -> callbackService.process(callback),
                Instant.now().plusMillis(delayMillis));
    }
}
