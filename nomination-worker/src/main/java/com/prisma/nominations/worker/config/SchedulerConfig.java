package com.prisma.nominations.worker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Planificador dedicado al simulador de ABM (entregas de callback con retraso).
 */
@Configuration
public class SchedulerConfig {

    @Bean
    public TaskScheduler abmMockScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("abm-mock-");
        scheduler.initialize();
        return scheduler;
    }
}
