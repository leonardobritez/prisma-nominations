package com.prisma.nominations.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utilidades de proteccion de datos. No se persisten ni exponen datos sensibles completos:
 * las referencias se enmascaran y el hash se usa para comparar payloads sin retener el original.
 */
public final class Masking {

    private Masking() {
    }

    /**
     * Enmascara cualquier referencia mostrando solo los ultimos 4 caracteres.
     * Ej.: "1234567890123456" -> "************3456" (truncado) o "****3456" segun longitud.
     */
    public static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }

    /**
     * Hash SHA-256 estable para comparar payloads de idempotencia sin almacenar el contenido.
     */
    public static String sha256(String value) {
        if (value == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
