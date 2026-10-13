package com.prisma.nominations.common.correlation;

import java.util.UUID;

/**
 * Contexto de correlacion por hilo de ejecucion.
 * Permite propagar correlationId / requestId entre capas (web, servicio, cliente HTTP, publicacion)
 * sin tener que pasarlos como parametro en cada firma.
 */
public final class CorrelationContext {

    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    private CorrelationContext() {
    }

    public static String getCorrelationId() {
        return CORRELATION_ID.get();
    }

    public static void setCorrelationId(String correlationId) {
        CORRELATION_ID.set(correlationId);
    }

    public static String getRequestId() {
        return REQUEST_ID.get();
    }

    public static void setRequestId(String requestId) {
        REQUEST_ID.set(requestId);
    }

    public static String getOrCreateCorrelationId() {
        String current = CORRELATION_ID.get();
        if (current == null || current.isBlank()) {
            current = UUID.randomUUID().toString();
            CORRELATION_ID.set(current);
        }
        return current;
    }

    public static void clear() {
        CORRELATION_ID.remove();
        REQUEST_ID.remove();
    }
}
