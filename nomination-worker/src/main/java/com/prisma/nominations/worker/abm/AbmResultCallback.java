package com.prisma.nominations.worker.abm;

import com.prisma.nominations.common.domain.NominationResult;

import java.util.UUID;

/**
 * Resultado asincronico entregado por ABM (por callback o sondeo).
 * Puede recibirse mas de una vez para la misma nominacion: el procesamiento es idempotente.
 *
 * @param nominationId identificador de la nominacion
 * @param externalRef  referencia externa asignada por ABM
 * @param result       resultado normalizado
 * @param rawReason    motivo crudo informado por ABM (se normaliza antes de persistir)
 * @param source       origen del callback (para trazabilidad/auditoria)
 */
public record AbmResultCallback(
        UUID nominationId,
        String externalRef,
        NominationResult result,
        String rawReason,
        String source
) {
}
