package com.prisma.nominations.persistence.repository;

import com.prisma.nominations.persistence.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);
}
