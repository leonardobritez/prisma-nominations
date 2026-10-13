package com.prisma.nominations.publisher.publish;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Publicacion en Kafka. Se espera la confirmacion del broker ({@code future.get}) para transformar
 * fallos de publicacion en excepciones y disparar la politica de reintentos/DLQ del relay.
 */
@Component
@Profile("kafka")
@Slf4j
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final String dlqTopic;
    private final Duration confirmTimeout;

    public KafkaEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.publisher.topic:prisma.nominations.result.v1}") String topic,
            @Value("${app.publisher.dlq-topic:prisma.nominations.result.v1.dlq}") String dlqTopic,
            @Value("${app.publisher.confirm-timeout:5s}") Duration confirmTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.dlqTopic = dlqTopic;
        this.confirmTimeout = confirmTimeout;
    }

    @Override
    public void publish(String key, String payload) {
        send(topic, key, payload);
    }

    @Override
    public void publishDeadLetter(String key, String payload, String error) {
        try {
            send(dlqTopic, key, payload);
        } catch (Exception e) {
            log.error("No se pudo publicar en DLQ para key={}: {}", key, e.getMessage());
        }
    }

    private void send(String destination, String key, String payload) {
        try {
            kafkaTemplate.send(destination, key, payload).get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            log.debug("Publicado en {} key={}", destination, key);
        } catch (Exception e) {
            throw new EventPublicationException(
                    "Fallo publicando en " + destination + " key=" + key + ": " + e.getMessage(), e);
        }
    }
}
