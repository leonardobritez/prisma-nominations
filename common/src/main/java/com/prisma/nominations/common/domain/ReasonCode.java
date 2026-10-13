package com.prisma.nominations.common.domain;

/**
 * Motivos normalizados de rechazo/falla. Evita propagar textos crudos internos de ABM
 * (que pueden filtrar informacion) y da un catalogo estable para los consumidores.
 */
public enum ReasonCode {
    ACCOUNT_NOT_FOUND,
    CARD_NOT_FOUND,
    CUSTOMER_BLOCKED,
    ALIAS_NOT_ALLOWED,
    INVALID_CARD_STATE,
    LIMIT_EXCEEDED,
    UPSTREAM_TIMEOUT,
    UPSTREAM_ERROR,
    UNKNOWN;

    public static ReasonCode fromExternal(String externalReason) {
        if (externalReason == null || externalReason.isBlank()) {
            return UNKNOWN;
        }
        String normalized = externalReason.trim().toUpperCase()
                .replace('-', '_')
                .replace(' ', '_');
        for (ReasonCode code : values()) {
            if (code.name().equals(normalized)) {
                return code;
            }
        }
        return UNKNOWN;
    }
}
