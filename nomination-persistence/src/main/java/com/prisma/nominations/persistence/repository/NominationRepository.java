package com.prisma.nominations.persistence.repository;

import com.prisma.nominations.common.domain.NominationStatus;
import com.prisma.nominations.persistence.domain.Nomination;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NominationRepository extends JpaRepository<Nomination, UUID> {

    /** Candidatas a despacho a ABM: estado RECEIVED y habilitadas por backoff. */
    List<Nomination> findByStatusAndNextAttemptAtLessThanEqual(
            NominationStatus status, Instant reference, Pageable pageable);

    /** Nominaciones enviadas a ABM que superaron la ventana de espera (posible timeout). */
    List<Nomination> findByStatusAndSentToAbmAtLessThanEqual(
            NominationStatus status, Instant threshold, Pageable pageable);

    long countByStatus(NominationStatus status);
}
