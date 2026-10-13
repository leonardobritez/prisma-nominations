package com.prisma.nominations.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * nomination-worker: procesamiento asincronico (envio a ABM, maquina de estados y outbox de resultado).
 * No expone API publica; solo un endpoint interno de callback de ABM y sus endpoints de observabilidad.
 */
@SpringBootApplication
@EntityScan("com.prisma.nominations.persistence.domain")
@EnableJpaRepositories("com.prisma.nominations.persistence.repository")
@ConfigurationPropertiesScan
@EnableScheduling
public class NominationWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(NominationWorkerApplication.class, args);
    }
}
