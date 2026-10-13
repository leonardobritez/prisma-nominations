package com.prisma.nominations.worker.abm;

/**
 * Rechazo funcional devuelto por ABM en el momento del envio. NO debe reintentarse:
 * reintentarlo no cambiaria el resultado y solo generaria ruido.
 */
public class AbmFunctionalException extends RuntimeException {

    private final String reasonCode;

    public AbmFunctionalException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
