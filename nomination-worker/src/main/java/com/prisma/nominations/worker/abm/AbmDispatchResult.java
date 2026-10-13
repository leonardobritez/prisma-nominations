package com.prisma.nominations.worker.abm;

/**
 * Resultado del envio a ABM.
 *
 * @param accepted    indica si ABM acepto el procesamiento
 * @param externalRef referencia externa asignada por ABM
 * @param reasonCode  motivo normalizado si fue rechazado funcionalmente
 */
public record AbmDispatchResult(boolean accepted, String externalRef, String reasonCode) {

    public static AbmDispatchResult accepted(String externalRef) {
        return new AbmDispatchResult(true, externalRef, null);
    }

    public static AbmDispatchResult rejected(String reasonCode) {
        return new AbmDispatchResult(false, null, reasonCode);
    }
}
