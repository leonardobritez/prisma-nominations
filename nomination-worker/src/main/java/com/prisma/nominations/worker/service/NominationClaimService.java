package com.prisma.nominations.worker.service;

import com.prisma.nominations.common.correlation.CorrelationContext;
import com.prisma.nominations.common.domain.NominationStatus;
import com.prisma.nominations.persistence.domain.Nomination;
import com.prisma.nominations.persistence.domain.NominationStatusHistory;
import com.prisma.nominations.persistence.repository.NominationRepository;
import com.prisma.nominations.persistence.repository.NominationStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Reclama (de forma atomica) una nominacion para su despacho. El reclamo y el envio a ABM se
 * separan en transacciones distintas para no mantener la conexion de base de datos abierta
 * durante una llamada remota lenta. La version optimista evita doble despacho entre instancias.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NominationClaimService {

    private final NominationRepository nominationRepository;
    private final NominationStatusHistoryRepository historyRepository;

    @Transactional
    public Optional<Nomination> claimForDispatch(UUID nominationId) {
        Optional<Nomination> found = nominationRepository.findById(nominationId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Nomination nomination = found.get();
        Instant now = Instant.now();
        if (nomination.getStatus() != NominationStatus.RECEIVED) {
            return Optional.empty();
        }
        if (nomination.getNextAttemptAt() != null && nomination.getNextAttemptAt().isAfter(now)) {
            return Optional.empty();
        }

        nomination.setStatus(NominationStatus.SENT_TO_ABM);
        nomination.setAttempts(nomination.getAttempts() + 1);
        nomination.setSentToAbmAt(now);
        nomination.setNextAttemptAt(null);
        nomination.setUpdatedAt(now);
        nominationRepository.save(nomination);

        recordHistory(nomination, NominationStatus.RECEIVED, NominationStatus.SENT_TO_ABM,
                null, "Envio a ABM (intento " + nomination.getAttempts() + ")", "WORKER");

        return Optional.of(nomination);
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
