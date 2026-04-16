package com.nmontytskyi.monitoring.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke test: verifies that the full Spring application context loads successfully.
 *
 * <p>Uses Testcontainers to spin up a real PostgreSQL instance so that
 * Flyway can apply migrations and JPA can validate the schema.
 * This test would fail if any of the following were broken:
 * <ul>
 *   <li>The {@code application.yml} configuration is invalid.</li>
 *   <li>Flyway migration {@code V1__init_schema.sql} contains SQL errors.</li>
 *   <li>A JPA entity does not match the Flyway-created schema
 *       (since {@code ddl-auto=validate}).</li>
 *   <li>Any Spring bean fails to initialise.</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
class MonitoringServerApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("monitoring")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void contextLoads() {
        // If the context starts without throwing an exception, the test passes.
    }
}
