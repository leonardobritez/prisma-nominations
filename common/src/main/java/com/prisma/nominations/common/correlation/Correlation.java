package com.prisma.nominations.common.correlation;

/**
 * Nombres de cabeceras usadas para la trazabilidad distribuida end-to-end.
 */
public final class Correlation {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_NOMINATION_ID = "nominationId";

    private Correlation() {
    }
}
