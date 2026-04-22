package com.nmontytskyi.monitoring.server.repository;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.config.JpaConfig;
import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity;
import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity.MetricSource;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the native percentile and error-count queries
 * added to {@link MetricRecordRepository} in Phase 8.
 *
 * <p>Uses a real PostgreSQL container so that {@code percentile_cont} and
 * the {@code error_flag} column are available exactly as in production.
 */
@DataJpaTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class MetricRecordRepositoryPercentileIT {

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
    private MetricRecordRepository metricRecordRepository;

    @Autowired
    private RegisteredServiceRepository serviceRepository;

    private RegisteredServiceEntity service;
    private final LocalDateTime base = LocalDateTime.of(2026, 4, 22, 12, 0, 0);

    @BeforeEach
    void setUp() {
        metricRecordRepository.deleteAll();
        serviceRepository.deleteAll();

        service = serviceRepository.save(
                RegisteredServiceEntity.builder()
                        .name("percentile-test-service")
                        .host("localhost")
                        .port(8080)
                        .build());
    }

    // ── findPercentiles ───────────────────────────────────────────────────────

    @Test
    void findPercentiles_withRecords_returnsCorrectP95() {
        // Insert 100 records with response times 1..100 ms
        for (int i = 1; i <= 100; i++) {
            metricRecordRepository.save(buildRecord(i, HealthStatus.UP, base.minusMinutes(i), false));
        }

        List<Object[]> result = metricRecordRepository.findPercentiles(
                service.getId(), base.minusHours(2));

        // PostgreSQL aggregates always return 1 row (with NULLs when empty)
        assertThat(result).hasSize(1);
        Object[] row = result.get(0);

        // p50 ≈ 50 ms, p95 ≈ 95 ms, p99 ≈ 99 ms  (PostgreSQL uses linear interpolation)
        double p50 = ((Number) row[0]).doubleValue();
        double p95 = ((Number) row[1]).doubleValue();
        double p99 = ((Number) row[2]).doubleValue();

        assertThat(p50).isBetween(49.0, 51.0);
        assertThat(p95).isBetween(94.0, 96.0);
        assertThat(p99).isBetween(98.0, 100.0);
    }

    @Test
    void findPercentiles_noRecords_returnsEmpty() {
        // No records exist for this service.
        // PostgreSQL ordered-set aggregates always return exactly one row with NULL
        // values when the input set is empty — the list is never truly empty.
        List<Object[]> result = metricRecordRepository.findPercentiles(
                service.getId(), base.minusHours(1));

        // Either the list is empty OR the single row has a null p50 value
        boolean isAbsent = result.isEmpty() || result.get(0)[0] == null;
        assertThat(isAbsent).isTrue();
    }

    @Test
    void findPercentiles_onlyOldRecordsExist_returnsEmpty() {
        // Record exists but is outside the query window
        metricRecordRepository.save(buildRecord(500, HealthStatus.UP, base.minusHours(5), false));

        List<Object[]> result = metricRecordRepository.findPercentiles(
                service.getId(), base.minusHours(1)); // window: last 1 hour

        boolean isAbsent = result.isEmpty() || result.get(0)[0] == null;
        assertThat(isAbsent).isTrue();
    }

    // ── countErrors ───────────────────────────────────────────────────────────

    @Test
    void countErrors_countsOnlyErrorFlagTrue() {
        // 3 error records + 2 clean records
        metricRecordRepository.save(buildRecord(100, HealthStatus.DOWN,     base.minusMinutes(1), true));
        metricRecordRepository.save(buildRecord(200, HealthStatus.DEGRADED, base.minusMinutes(2), true));
        metricRecordRepository.save(buildRecord(150, HealthStatus.UP,       base.minusMinutes(3), true));
        metricRecordRepository.save(buildRecord(120, HealthStatus.UP,       base.minusMinutes(4), false));
        metricRecordRepository.save(buildRecord(130, HealthStatus.UP,       base.minusMinutes(5), false));

        long count = metricRecordRepository.countErrors(
                service.getId(), base.minusHours(1));

        assertThat(count).isEqualTo(3L);
    }

    @Test
    void countErrors_noErrorRecords_returnsZero() {
        metricRecordRepository.save(buildRecord(100, HealthStatus.UP, base.minusMinutes(1), false));
        metricRecordRepository.save(buildRecord(200, HealthStatus.UP, base.minusMinutes(2), false));

        long count = metricRecordRepository.countErrors(
                service.getId(), base.minusHours(1));

        assertThat(count).isEqualTo(0L);
    }

    @Test
    void countErrors_outsideWindow_notCounted() {
        // Only this old record has error_flag = true, but it's outside the window
        metricRecordRepository.save(buildRecord(300, HealthStatus.DOWN, base.minusHours(5), true));

        long count = metricRecordRepository.countErrors(
                service.getId(), base.minusHours(1)); // window: last 1 hour

        assertThat(count).isEqualTo(0L);
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private MetricRecordEntity buildRecord(
            long responseTimeMs,
            HealthStatus status,
            LocalDateTime recordedAt,
            boolean errorFlag
    ) {
        return MetricRecordEntity.builder()
                .service(service)
                .responseTimeMs(responseTimeMs)
                .status(status)
                .errorFlag(errorFlag)
                .source(MetricSource.PUSH)
                .recordedAt(recordedAt)
                .build();
    }
}
