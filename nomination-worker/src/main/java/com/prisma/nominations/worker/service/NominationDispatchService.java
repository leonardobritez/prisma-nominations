package com.prisma.nominations.worker.service;

import com.prisma.nominations.common.correlation.CorrelationContext;
import com.prisma.nominations.common.domain.NominationStatus;
import com.prisma.nominations.persistence.domain.Nomination;
import com.prisma.nominations.persistence.repository.NominationRepository;
import com.prisma.nominations.worker.abm.AbmDispatchResult;
import com.prisma.nominations.worker.abm.AbmFunctionalException;
import com.prisma.nominations.worker.abm.AbmGateway;
import com.prisma.nominations.worker.abm.AbmTechnicalException;
import com.prisma.nominations.worker.config.AbmProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquesta el despacho asincronico hacia ABM:
 * <ol>
 *     <li>Reclama la nominacion (tx corta).</li>
 *     <li>Invoca a ABM fuera de transaccion (no bloquea conexiones).</li>
 *     <li>Aplica el desenlace (tx corta) o programa reintento.</li>
 * </ol>
 * Ademas recupera nominaciones "colgadas" en SENT_TO_ABM que superaron la ventana de espera.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NominationDispatchService {

    private final NominationRepository nominationRepository;
    private final NominationClaimService claimService;
    private final NominationOutcomeService outcomeService;
    private final AbmGateway abmGateway;
    private final AbmProperties properties;

    public int dispatchPending() {
        List<Nomination> due = nominationRepository.findByStatusAndNextAttemptAtLessThanEqual(
                NominationStatus.RECEIVED, Instant.now(), PageRequest.of(0, properties.getBatchSize()));
        int processed = 0;
        for (Nomination candidate : due) {
            if (dispatchOne(candidate.getId())) {
                processed++;
            }
        }
        return processed;
    }

    public boolean dispatchOne(UUID nominationId) {
        Optional<Nomination> claimed;
        try {
            claimed = claimService.claimForDispatch(nominationId);
        } catch (OptimisticLockingFailureException e) {
            log.debug("Nominacion {} ya reclamada por otra instancia", nominationId);
            return false;
        }
        if (claimed.isEmpty()) {
            return false;
        }
        Nomination nomination = claimed.get();
        CorrelationContext.setCorrelationId(nomination.getCorrelationId());
        try {
            AbmDispatchResult result = abmGateway.send(nomination);
            if (result.accepted()) {
                outcomeService.applyAccepted(nomination.getId(), result.externalRef());
            } else {
                outcomeService.applyFunctionalRejection(nomination.getId(), result.reasonCode());
            }
        } catch (AbmFunctionalException e) {
            outcomeService.applyFunctionalRejection(nomination.getId(), e.getReasonCode());
        } catch (AbmTechnicalException e) {
            outcomeService.applyTechnicalFailure(nomination.getId(), e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado despachando nominacion {}", nominationId, e);
            outcomeService.applyTechnicalFailure(nomination.getId(), e.getMessage());
        } finally {
            CorrelationContext.clear();
        }
        return true;
    }

    public int recoverStale() {
        Instant threshold = Instant.now().minus(properties.getWaitWindow());
        List<Nomination> stale = nominationRepository.findByStatusAndSentToAbmAtLessThanEqual(
                NominationStatus.SENT_TO_ABM, threshold, PageRequest.of(0, properties.getBatchSize()));
        for (Nomination nomination : stale) {
            log.warn("Recuperando nominacion sin respuesta de ABM id={} intentos={}",
                    nomination.getId(), nomination.getAttempts());
            outcomeService.applyTimeout(nomination.getId());
        }
        return stale.size();
    }
}
