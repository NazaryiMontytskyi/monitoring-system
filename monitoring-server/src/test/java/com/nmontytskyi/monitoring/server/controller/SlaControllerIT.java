package com.nmontytskyi.monitoring.server.controller;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.model.SlaReport;
import com.nmontytskyi.monitoring.server.dto.request.MetricSnapshotRequest;
import com.nmontytskyi.monitoring.server.dto.request.ServiceRegistrationRequest;
import com.nmontytskyi.monitoring.server.dto.response.ServiceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@code GET /api/services/{id}/sla}.
 *
 * <p>Boots the full application context with a real PostgreSQL container
 * and exercises the REST endpoint end-to-end.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SlaControllerIT {

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
    private TestRestTemplate restTemplate;

    // ── 1. Existing service returns 200 with a valid SlaReport ───────────────

    @Test
    void getSla_existingService_returns200WithReport() {
        Long serviceId = registerService("sla-it-service-1");

        ResponseEntity<SlaReport> response = restTemplate.getForEntity(
                "/api/services/" + serviceId + "/sla", SlaReport.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        SlaReport report = response.getBody();
        assertThat(report.getServiceId()).isEqualTo(String.valueOf(serviceId));
        assertThat(report.getFrom()).isNotNull();
        assertThat(report.getTo()).isNotNull();
        assertThat(report.getSla()).isNotNull();
        // No records yet → 100% uptime and 0% error rate by convention
        assertThat(report.getActualUptimePercent()).isEqualTo(100.0);
        assertThat(report.getActualErrorRatePercent()).isEqualTo(0.0);
    }

    // ── 2. Unknown service ID → 404 ──────────────────────────────────────────

    @Test
    void getSla_unknownService_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/services/999999/sla", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── 3. Default window is DAY ──────────────────────────────────────────────

    @Test
    void getSla_defaultWindowIsDay() {
        Long serviceId = registerService("sla-it-service-day");

        // No explicit ?window= parameter → defaults to DAY
        ResponseEntity<SlaReport> noParam = restTemplate.getForEntity(
                "/api/services/" + serviceId + "/sla", SlaReport.class);

        // Explicit ?window=DAY
        ResponseEntity<SlaReport> explicit = restTemplate.getForEntity(
                "/api/services/" + serviceId + "/sla?window=DAY", SlaReport.class);

        assertThat(noParam.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(explicit.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Both windows should match to within a few seconds of each other
        assertThat(noParam.getBody()).isNotNull();
        assertThat(explicit.getBody()).isNotNull();
        assertThat(noParam.getBody().getActualUptimePercent())
                .isEqualTo(explicit.getBody().getActualUptimePercent());
    }

    // ── 4. WEEK window returns 200 ────────────────────────────────────────────

    @Test
    void getSla_weekWindow_returns200() {
        Long serviceId = registerService("sla-it-service-week");

        ResponseEntity<SlaReport> response = restTemplate.getForEntity(
                "/api/services/" + serviceId + "/sla?window=WEEK", SlaReport.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getServiceId()).isEqualTo(String.valueOf(serviceId));
    }

    // ── 5. Report reflects pushed metrics ────────────────────────────────────

    @Test
    void getSla_withMetrics_reflectsActualData() {
        Long serviceId = registerService("sla-it-service-metrics");

        // Push some UP and DOWN snapshots
        pushMetric(serviceId, "GET /api", 200L, HealthStatus.UP);
        pushMetric(serviceId, "GET /api", 300L, HealthStatus.UP);
        pushMetric(serviceId, "GET /api", 100L, HealthStatus.DOWN);

        ResponseEntity<SlaReport> response = restTemplate.getForEntity(
                "/api/services/" + serviceId + "/sla?window=HOUR", SlaReport.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SlaReport report = response.getBody();
        assertThat(report).isNotNull();
        // 2 UP out of 3 total → ~66.67% uptime
        assertThat(report.getActualUptimePercent()).isBetween(66.0, 67.0);
        assertThat(report.getActualAvgResponseTimeMs()).isGreaterThan(0.0);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Long registerService(String name) {
        ServiceRegistrationRequest req = ServiceRegistrationRequest.builder()
                .name(name)
                .host("localhost")
                .port(9090)
                .actuatorUrl("http://localhost:9090/actuator")
                .baseUrl("http://localhost:9090")
                .build();

        ResponseEntity<ServiceResponse> response = restTemplate.postForEntity(
                "/api/services", req, ServiceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().getId();
    }

    private void pushMetric(Long serviceId, String endpoint, long responseTimeMs, HealthStatus status) {
        MetricSnapshotRequest req = MetricSnapshotRequest.builder()
                .serviceId(serviceId)
                .endpoint(endpoint)
                .responseTimeMs(responseTimeMs)
                .status(status)
                .build();
        restTemplate.postForEntity("/api/metrics/endpoint", req, String.class);
    }
}
