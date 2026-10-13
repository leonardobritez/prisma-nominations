package com.prisma.nominations.publisher.publish;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Sink de publicacion para entornos locales y pruebas: registra el evento como log estructurado
 * en lugar de enviarlo a un broker. Permite ejecutar end-to-end sin infraestructura de mensajeria.
 */
@Component
@Profile("!kafka")
@Slf4j
public class LoggingEventPublisher implements EventPublisher {

    @Override
    public void publish(String key, String payload) {
        log.info("EVENTO PUBLICADO key={} payload={}", key, payload);
    }

    @Override
    public void publishDeadLetter(String key, String payload, String error) {
        log.error("EVENTO A DEAD-LETTER key={} error={} payload={}", key, error, payload);
    }
}
