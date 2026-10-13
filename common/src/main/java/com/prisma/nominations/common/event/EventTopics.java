package com.prisma.nominations.common.event;

/**
 * Nombres de topics/colas y tipos de evento. Versionados para permitir evolucion compatible.
 */
public final class EventTopics {

    public static final String NOMINATION_RESULT_TOPIC = "prisma.nominations.result.v1";
    public static final String NOMINATION_RESULT_DLQ_TOPIC = "prisma.nominations.result.v1.dlq";
    public static final String NOMINATION_RESULT_EVENT_TYPE = "nomination.result";

    private EventTopics() {
    }
}
