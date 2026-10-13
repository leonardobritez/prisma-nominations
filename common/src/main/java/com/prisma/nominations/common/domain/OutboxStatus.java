package com.prisma.nominations.common.domain;

/**
 * Ciclo de vida de un registro de outbox.
 * Garantiza consistencia entre el estado persistido y la publicacion del evento.
 */
public enum OutboxStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED,
    DLQ
}
