package com.prisma.nominations.common.error;

/**
 * Catalogo estable de codigos de error expuestos por la API.
 * Estos codigos son parte del contrato y no deben cambiar de significado entre versiones.
 */
public enum ErrorCode {
    MISSING_FIELD,
    INVALID_FORMAT,
    INVALID_ALIAS,
    IDEMPOTENCY_CONFLICT,
    IDEMPOTENCY_IN_PROGRESS,
    NOT_FOUND,
    UPSTREAM_TIMEOUT,
    UPSTREAM_ERROR,
    INTERNAL_ERROR,
    UNAUTHORIZED,
    FORBIDDEN,
    RATE_LIMITED
}
