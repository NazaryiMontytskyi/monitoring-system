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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MetricRecordRepository}.
 *
 * <p>Validates time-range queries, the anomaly-detection window query,
 * the aggregate query, and the endpoint breakdown query — all of which
 * will be used heavily by the REST API (FR-2) and dashboard (FR-3).
 */
@DataJpaTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class MetricRecordRepositoryIT {

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
    private final LocalDateTime baseTime = LocalDateTime.of(2026, 4, 16, 10, 0, 0);

    @BeforeEach
    void setUp() {
        service = serviceRepository.save(
                RegisteredServiceEntity.builder()
                        .name("test-service")
                        .host("localhost")
                        .port(8080)
                        .build()
        );
    }

    @Test
    void save_and_findById_roundTrip() {
        MetricRecordEntity record = buildRecord(200L, HealthStatus.UP, baseTime, MetricSource.PULL);

        MetricRecordEntity saved = metricRecordRepository.save(record);

        assertThat(saved.getId()).isNotNull();
        Optional<MetricRecordEntity> found = metricRecordRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getResponseTimeMs()).isEqualTo(200L);
        assertThat(found.get().getSource()).isEqualTo(MetricSource.PULL);
    }

    @Test
    void findTop100ByServiceId_returnsNewestFirst() {
        for (int i = 0; i < 5; i++) {
            metricRecordRepository.save(
                    buildRecord(100L + i * 10, HealthStatus.UP,
                            baseTime.plusMinutes(i), MetricSource.PULL)
            );
        }

        List<MetricRecordEntity> result =
                metricRecordRepository.findTop100ByServiceIdOrderByRecordedAtDesc(service.getId());

        assertThat(result).hasSize(5);
        // Newest first: last saved has highest responseTimeMs
        assertThat(result.get(0).getResponseTimeMs()).isEqualTo(140L);
        assertThat(result.get(4).getResponseTimeMs()).isEqualTo(100L);
    }

    @Test
    void findByServiceIdAndRecordedAtBetween_filtersCorrectly() {
        metricRecordRepository.save(buildRecord(100L, HealthStatus.UP,
                baseTime.minusHours(2), MetricSource.PULL));
        metricRecordRepository.save(buildRecord(200L, HealthStatus.UP,
                baseTime, MetricSource.PULL));
        metricRecordRepository.save(buildRecord(300L, HealthStatus.UP,
                baseTime.plusHours(1), MetricSource.PUSH));
        metricRecordRepository.save(buildRecord(400L, HealthStatus.DOWN,
                baseTime.plusHours(3), MetricSource.PULL));

        List<MetricRecordEntity> result =
                metricRecordRepository.findByServiceIdAndRecordedAtBetweenOrderByRecordedAtAsc(
                        service.getId(),
                        baseTime.minusMinutes(1),
                        baseTime.plusHours(2)
                );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getResponseTimeMs()).isEqualTo(200L);
        assertThat(result.get(1).getResponseTimeMs()).isEqualTo(300L);
    }

    @Test
    void findTopByServiceId_returnsLatestRecord() {
        metricRecordRepository.save(buildRecord(100L, HealthStatus.UP,
                baseTime.minusMinutes(10), MetricSource.PULL));
        metricRecordRepository.save(buildRecord(999L, HealthStatus.DEGRADED,
                baseTime, MetricSource.PUSH));

        Optional<MetricRecordEntity> latest =
                metricRecordRepository.findTopByServiceIdOrderByRecordedAtDesc(service.getId());

        assertThat(latest).isPresent();
        assertThat(latest.get().getResponseTimeMs()).isEqualTo(999L);
        assertThat(latest.get().getStatus()).isEqualTo(HealthStatus.DEGRADED);
    }

    @Test
    void aggregateByServiceAndPeriod_computesCorrectStats() {
        metricRecordRepository.save(buildRecord(100L, HealthStatus.UP, baseTime, MetricSource.PULL));
        metricRecordRepository.save(buildRecord(200L, HealthStatus.UP, baseTime.plusMinutes(5), MetricSource.PULL));
        metricRecordRepository.save(buildRecord(300L, HealthStatus.DOWN, baseTime.plusMinutes(10), MetricSource.PULL));

        List<Object[]> results = metricRecordRepository.aggregateByServiceAndPeriod(
                service.getId(),
                baseTime.minusMinutes(1),
                baseTime.plusMinutes(15)
        );

        assertThat(results).hasSize(1);
        Object[] row = results.get(0);
        double avg = ((Number) row[0]).doubleValue();
        long min = ((Number) row[1]).longValue();
        long max = ((Number) row[2]).longValue();
        long total = ((Number) row[3]).longValue();
        long upCount = ((Number) row[4]).longValue();

        assertThat(avg).isEqualTo(200.0);
        assertThat(min).isEqualTo(100L);
        assertThat(max).isEqualTo(300L);
        assertThat(total).isEqualTo(3L);
        assertThat(upCount).isEqualTo(2L);
    }

    @Test
    void countByEndpointAndPeriod_groupsCorrectly() {
        metricRecordRepository.save(buildPushRecord("GET /orders", 150L, baseTime));
        metricRecordRepository.save(buildPushRecord("GET /orders", 250L, baseTime.plusMinutes(1)));
        metricRecordRepository.save(buildPushRecord("POST /orders", 120L, baseTime.plusMinutes(2)));

        List<Object[]> result = metricRecordRepository.countByEndpointAndPeriod(
                service.getId(),
                baseTime.minusMinutes(1),
                baseTime.plusMinutes(5)
        );

        assertThat(result).hasSize(2);
        // GET /orders appears 2 times, POST /orders once — ordered by count DESC
        assertThat(result.get(0)[0]).isEqualTo("GET /orders");
        assertThat(((Number) result.get(0)[1]).longValue()).isEqualTo(2L);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private MetricRecordEntity buildRecord(
            long responseTimeMs, HealthStatus status,
            LocalDateTime recordedAt, MetricSource source
    ) {
        return MetricRecordEntity.builder()
                .service(service)
                .responseTimeMs(responseTimeMs)
                .status(status)
                .recordedAt(recordedAt)
                .source(source)
                .build();
    }

    private MetricRecordEntity buildPushRecord(
            String endpoint, long responseTimeMs, LocalDateTime recordedAt
    ) {
        return MetricRecordEntity.builder()
                .service(service)
                .endpoint(endpoint)
                .responseTimeMs(responseTimeMs)
                .status(HealthStatus.UP)
                .recordedAt(recordedAt)
                .source(MetricSource.PUSH)
                .build();
    }
}
