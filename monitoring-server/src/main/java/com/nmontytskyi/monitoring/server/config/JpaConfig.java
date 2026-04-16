package com.nmontytskyi.monitoring.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA configuration for the Monitoring Server.
 *
 * <p>Enables JPA auditing so that {@code @CreatedDate} and {@code @LastModifiedDate}
 * annotations on entity fields are automatically populated by Spring Data JPA.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
