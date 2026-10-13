package com.prisma.nominations.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * nomination-api: borde sincrono de la plataforma de nominaciones.
 */
@SpringBootApplication
@EntityScan("com.prisma.nominations.persistence.domain")
@EnableJpaRepositories("com.prisma.nominations.persistence.repository")
public class NominationApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(NominationApiApplication.class, args);
    }
}
