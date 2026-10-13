package com.prisma.nominations.api.service;

/**
 * Se lanza cuando la clave de idempotencia se reutiliza con un cuerpo distinto.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
