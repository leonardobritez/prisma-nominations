package com.prisma.nominations.persistence.repository;

import com.prisma.nominations.common.domain.OutboxStatus;
import com.prisma.nominations.persistence.domain.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            OutboxStatus status, Instant reference, Pageable pageable);

    long countByStatus(OutboxStatus status);
}
