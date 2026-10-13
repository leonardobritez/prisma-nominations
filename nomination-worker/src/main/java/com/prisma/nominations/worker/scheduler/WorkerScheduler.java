package com.prisma.nominations.worker.scheduler;

import com.prisma.nominations.worker.service.NominationDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Disparadores periodicos del worker. El procesamiento es idempotente y seguro entre instancias
 * (el reclamo usa version optimista), de modo que escalar horizontalmente no genera doble envio.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkerScheduler {

    private final NominationDispatchService dispatchService;

    @Scheduled(fixedDelayString = "${app.worker.dispatch-interval-ms:1000}")
    public void dispatchPending() {
        try {
            int processed = dispatchService.dispatchPending();
            if (processed > 0) {
                log.debug("Ciclo de despacho: {} nominaciones procesadas", processed);
            }
        } catch (Exception e) {
            log.error("Error en el ciclo de despacho", e);
        }
    }

    @Scheduled(fixedDelayString = "${app.worker.recovery-interval-ms:5000}")
    public void recoverStale() {
        try {
            int recovered = dispatchService.recoverStale();
            if (recovered > 0) {
                log.warn("Ciclo de recuperacion: {} nominaciones sin respuesta atendidas", recovered);
            }
        } catch (Exception e) {
            log.error("Error en el ciclo de recuperacion", e);
        }
    }
}
