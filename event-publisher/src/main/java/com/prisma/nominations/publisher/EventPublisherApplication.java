package com.prisma.nominations.publisher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * event-publisher: lee el outbox y publica el evento unico de resultado, con reintentos y DLQ.
 * Desacopla la publicacion de la transaccion de negocio (patron outbox + relay).
 */
@SpringBootApplication
@EntityScan("com.prisma.nominations.persistence.domain")
@EnableJpaRepositories("com.prisma.nominations.persistence.repository")
@EnableScheduling
public class EventPublisherApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventPublisherApplication.class, args);
    }
}
