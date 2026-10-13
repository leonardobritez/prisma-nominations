package com.prisma.nominations.common.domain;

/**
 * Resultado normalizado de ABM.
 * Se separa el rechazo funcional (REJECTED) de las fallas tecnicas (FAILED / TIMEOUT).
 */
public enum NominationResult {
    APPROVED,
    REJECTED,
    FAILED,
    TIMEOUT
}
