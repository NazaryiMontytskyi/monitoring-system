package com.nmontytskyi.monitoring.server.repository;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.config.JpaConfig;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import com.nmontytskyi.monitoring.server.entity.SlaDefinitionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link RegisteredServiceRepository}.
 *
 * <p>Uses {@code @DataJpaTest} with a real PostgreSQL container (Testcontainers)
 * instead of an in-memory H2 database. This ensures that PostgreSQL-specific SQL
 * in the Flyway migration is valid and that the JPA schema validation passes.
 */
@DataJpaTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class RegisteredServiceRepositoryIT {

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

    @Autowired
    private RegisteredServiceRepository repository;

    @Test
    void save_and_findById_roundTrip() {
        RegisteredServiceEntity service = buildService("order-service", 8081);

        RegisteredServiceEntity saved = repository.save(service);

        assertThat(saved.getId()).isNotNull();
        Optional<RegisteredServiceEntity> found = repository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("order-service");
        assertThat(found.get().getStatus()).isEqualTo(HealthStatus.UNKNOWN);
    }

    @Test
    void findByName_returnsService_whenExists() {
        repository.save(buildService("inventory-service", 8082));

        Optional<RegisteredServiceEntity> result = repository.findByName("inventory-service");

        assertThat(result).isPresent();
        assertThat(result.get().getPort()).isEqualTo(8082);
    }

    @Test
    void findByName_returnsEmpty_whenNotExists() {
        Optional<RegisteredServiceEntity> result = repository.findByName("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void existsByName_returnsTrue_whenServiceRegistered() {
        repository.save(buildService("payment-service", 8083));

        assertThat(repository.existsByName("payment-service")).isTrue();
        assertThat(repository.existsByName("unknown-service")).isFalse();
    }

    @Test
    void findAllByStatus_filtersCorrectly() {
        RegisteredServiceEntity upService = buildService("up-service", 8084);
        upService.setStatus(HealthStatus.UP);

        RegisteredServiceEntity downService = buildService("down-service", 8085);
        downService.setStatus(HealthStatus.DOWN);

        repository.saveAll(List.of(upService, downService));

        List<RegisteredServiceEntity> upServices = repository.findAllByStatus(HealthStatus.UP);
        assertThat(upServices).hasSize(1);
        assertThat(upServices.get(0).getName()).isEqualTo("up-service");

        List<RegisteredServiceEntity> downServices = repository.findAllByStatus(HealthStatus.DOWN);
        assertThat(downServices).hasSize(1);
        assertThat(downServices.get(0).getName()).isEqualTo("down-service");
    }

    @Test
    void slaDefinition_isPersistedWithService() {
        RegisteredServiceEntity service = buildService("sla-service", 8086);
        SlaDefinitionEntity sla = SlaDefinitionEntity.builder()
                .uptimePercent(99.9)
                .maxResponseTimeMs(300L)
                .maxErrorRatePercent(1.0)
                .description("Strict SLA")
                .build();
        sla.setService(service);
        service.setSlaDefinition(sla);

        RegisteredServiceEntity saved = repository.save(service);
        repository.flush();

        Optional<RegisteredServiceEntity> found = repository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getSlaDefinition()).isNotNull();
        assertThat(found.get().getSlaDefinition().getMaxResponseTimeMs()).isEqualTo(300L);
        assertThat(found.get().getSlaDefinition().getDescription()).isEqualTo("Strict SLA");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private RegisteredServiceEntity buildService(String name, int port) {
        return RegisteredServiceEntity.builder()
                .name(name)
                .host("localhost")
                .port(port)
                .actuatorUrl("http://localhost:" + port + "/actuator")
                .baseUrl("http://localhost:" + port)
                .build();
    }
}
