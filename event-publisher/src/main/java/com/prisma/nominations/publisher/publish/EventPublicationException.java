package com.prisma.nominations.publisher.publish;

/**
 * Falla al publicar un evento en el broker. Dispara reintentos con backoff y, eventualmente, DLQ.
 */
public class EventPublicationException extends RuntimeException {

    public EventPublicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
