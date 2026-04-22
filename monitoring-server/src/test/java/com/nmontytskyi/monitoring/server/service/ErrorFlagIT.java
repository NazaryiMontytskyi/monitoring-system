package com.nmontytskyi.monitoring.server.service;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.dto.request.MetricSnapshotRequest;
import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import com.nmontytskyi.monitoring.server.repository.MetricRecordRepository;
import com.nmontytskyi.monitoring.server.repository.RegisteredServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying that {@code MetricsPersistenceService} correctly
 * sets the {@code error_flag} field on saved {@link MetricRecordEntity} instances.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ErrorFlagIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("monitoring.polling.enabled", () -> "false");
        registry.add("management.health.mail.enabled", () -> "false");
    }

    @MockBean
    private JavaMailSender javaMailSender;

    @Autowired
    private MetricsPersistenceService metricsPersistenceService;

    @Autowired
    private MetricRecordRepository metricRecordRepository;

    @Autowired
    private RegisteredServiceRepository serviceRepository;

    private RegisteredServiceEntity service;

    @BeforeEach
    void setUp() {
        metricRecordRepository.deleteAll();
        serviceRepository.deleteAll();

        service = serviceRepository.save(
                RegisteredServiceEntity.builder()
                        .name("errorflag-test-service")
                        .host("localhost")
                        .port(9090)
                        .build());
    }

    // ── DOWN status → error_flag = true ──────────────────────────────────────

    @Test
    void saveMetric_withDownStatus_setsErrorFlagTrue() {
        MetricSnapshotRequest request = MetricSnapshotRequest.builder()
                .serviceId(service.getId())
                .endpoint("GET /health")
                .responseTimeMs(500L)
                .status(HealthStatus.DOWN)
                .build();

        metricsPersistenceService.saveEndpointSnapshot(request);

        List<MetricRecordEntity> records = metricRecordRepository
                .findTop100ByServiceIdOrderByRecordedAtDesc(service.getId());
        assertThat(records).hasSize(1);
        assertThat(records.get(0).isErrorFlag()).isTrue();
    }

    // ── DEGRADED status → error_flag = true ──────────────────────────────────

    @Test
    void saveMetric_withDegradedStatus_setsErrorFlagTrue() {
        MetricSnapshotRequest request = MetricSnapshotRequest.builder()
                .serviceId(service.getId())
                .endpoint("GET /api")
                .responseTimeMs(800L)
                .status(HealthStatus.DEGRADED)
                .build();

        metricsPersistenceService.saveEndpointSnapshot(request);

        List<MetricRecordEntity> records = metricRecordRepository
                .findTop100ByServiceIdOrderByRecordedAtDesc(service.getId());
        assertThat(records).hasSize(1);
        assertThat(records.get(0).isErrorFlag()).isTrue();
    }

    // ── non-null errorMessage → error_flag = true ─────────────────────────────

    @Test
    void saveMetric_withErrorMessage_setsErrorFlagTrue() {
        MetricSnapshotRequest request = MetricSnapshotRequest.builder()
                .serviceId(service.getId())
                .endpoint("POST /orders")
                .responseTimeMs(300L)
                .status(HealthStatus.UP)
                .errorMessage("NullPointerException in OrderService")
                .build();

        metricsPersistenceService.saveEndpointSnapshot(request);

        List<MetricRecordEntity> records = metricRecordRepository
                .findTop100ByServiceIdOrderByRecordedAtDesc(service.getId());
        assertThat(records).hasSize(1);
        assertThat(records.get(0).isErrorFlag()).isTrue();
    }

    // ── UP + no errorMessage → error_flag = false ─────────────────────────────

    @Test
    void saveMetric_withUpStatusAndNoError_setsErrorFlagFalse() {
        MetricSnapshotRequest request = MetricSnapshotRequest.builder()
                .serviceId(service.getId())
                .endpoint("GET /items")
                .responseTimeMs(120L)
                .status(HealthStatus.UP)
                .build();

        metricsPersistenceService.saveEndpointSnapshot(request);

        List<MetricRecordEntity> records = metricRecordRepository
                .findTop100ByServiceIdOrderByRecordedAtDesc(service.getId());
        assertThat(records).hasSize(1);
        assertThat(records.get(0).isErrorFlag()).isFalse();
    }
}
