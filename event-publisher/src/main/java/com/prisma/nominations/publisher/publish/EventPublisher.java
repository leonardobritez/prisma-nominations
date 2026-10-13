package com.prisma.nominations.publisher.publish;

/**
 * Puerto de salida del publisher. La implementacion concreta (Kafka o sink de log) se selecciona
 * por perfil. Un fallo de publicacion debe propagarse como excepcion para disparar reintentos.
 */
public interface EventPublisher {

    /** Publica el evento en el topic principal. */
    void publish(String key, String payload);

    /** Publica en el topic de dead-letter cuando se agotan los reintentos. */
    void publishDeadLetter(String key, String payload, String error);
}
