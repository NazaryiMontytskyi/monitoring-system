package com.nmontytskyi.monitoring.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * JPA configuration for the Monitoring Server.
 *
 * <p>Enables JPA auditing so that {@code @CreatedDate} and {@code @LastModifiedDate}
 * annotations on entity fields are automatically populated by Spring Data JPA.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("retention-");
        return scheduler;
    }
}
