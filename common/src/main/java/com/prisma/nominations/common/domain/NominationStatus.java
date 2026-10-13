package com.prisma.nominations.common.domain;

/**
 * Estados posibles de una nominacion.
 *
 * <pre>
 * RECEIVED ---------> SENT_TO_ABM ------> APPROVED
 *    |                     |              REJECTED   (rechazo funcional de ABM)
 *    |                     |              FAILED     (falla tecnica no recuperable)
 *    |                     |              TIMEOUT    (ABM sin respuesta, reintentos agotados)
 *    |
 *    +----------------> VALIDATION_FAILED    (rechazo inmediato en el borde sincrono)
 * </pre>
 */
public enum NominationStatus {
    RECEIVED,
    VALIDATION_FAILED,
    SENT_TO_ABM,
    APPROVED,
    REJECTED,
    FAILED,
    TIMEOUT;

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED || this == FAILED
                || this == TIMEOUT || this == VALIDATION_FAILED;
    }
}
