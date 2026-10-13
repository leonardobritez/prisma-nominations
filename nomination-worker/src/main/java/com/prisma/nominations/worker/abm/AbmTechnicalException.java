package com.prisma.nominations.worker.abm;

/**
 * Falla tecnica recuperable en la integracion con ABM (timeout, error de red, 5xx).
 * Es candidata a reintento segun la politica configurada.
 */
public class AbmTechnicalException extends RuntimeException {

    public AbmTechnicalException(String message) {
        super(message);
    }

    public AbmTechnicalException(String message, Throwable cause) {
        super(message, cause);
    }
}
