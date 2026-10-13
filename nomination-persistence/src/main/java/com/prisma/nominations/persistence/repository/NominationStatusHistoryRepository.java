package com.prisma.nominations.persistence.repository;

import com.prisma.nominations.persistence.domain.NominationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NominationStatusHistoryRepository extends JpaRepository<NominationStatusHistory, UUID> {

    List<NominationStatusHistory> findByNominationIdOrderByOccurredAtAsc(UUID nominationId);
}
