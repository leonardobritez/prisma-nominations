package com.prisma.nominations.common.event;

import com.prisma.nominations.common.domain.NominationResult;
import com.prisma.nominations.common.domain.NominationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento unico de resultado de una nominacion.
 *
 * <p>Contrato inmutable y versionado. Se publica exactamente un evento terminal por nominacion.
 * Reglas de evolucion: solo se agregan campos opcionales; nunca se renombra ni se cambia el tipo
 * de un campo existente. El consumidor debe ignorar campos desconocidos.</p>
 *
 * @param eventId       identificador unico del evento (clave de deduplicacion en consumidores)
 * @param eventType     tipo logico del evento ({@code nomination.result})
 * @param eventVersion  version del contrato del evento
 * @param nominationId  identificador de la nominacion (clave de agregado / particion)
 * @param customerId    identificador del cliente (seudonimizado)
 * @param accountId     identificador de cuenta (enmascarado)
 * @param cardMask      referencia enmascarada de tarjeta (nunca PAN completo)
 * @param alias         alias de la cuenta
 * @param status        estado final persistido
 * @param result        resultado normalizado
 * @param reasonCode    codigo de motivo normalizado (puede ser nulo si APPROVED)
 * @param reasonMessage mensaje de motivo legible (sin datos sensibles)
 * @param attempts      cantidad de intentos realizados contra ABM
 * @param correlationId identificador de correlacion end-to-end
 * @param occurredAt    instante en que se produjo el resultado
 */
public record NominationResultEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        UUID nominationId,
        String customerId,
        String accountId,
        String cardMask,
        String alias,
        NominationStatus status,
        NominationResult result,
        String reasonCode,
        String reasonMessage,
        int attempts,
        String correlationId,
        Instant occurredAt
) {
    public static final String TYPE = EventTopics.NOMINATION_RESULT_EVENT_TYPE;
    public static final int CURRENT_VERSION = 1;
}
