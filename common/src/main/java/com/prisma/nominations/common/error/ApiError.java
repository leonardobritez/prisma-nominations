package com.prisma.nominations.common.error;

import java.util.List;

/**
 * Modelo de error consistente (RFC 7807-like) para todas las respuestas de error de la API.
 *
 * @param timestamp     instante del error en ISO-8601
 * @param status        codigo HTTP
 * @param code          codigo de error estable (parte del contrato)
 * @param message       mensaje legible por humanos (sin datos sensibles)
 * @param path          ruta solicitada
 * @param correlationId identificador de correlacion para trazabilidad
 * @param errors        detalle de campos invalidos (puede ser vacio)
 */
public record ApiError(
        String timestamp,
        int status,
        ErrorCode code,
        String message,
        String path,
        String correlationId,
        List<FieldError> errors
) {
    public record FieldError(String field, String code, String message) {
    }
}
