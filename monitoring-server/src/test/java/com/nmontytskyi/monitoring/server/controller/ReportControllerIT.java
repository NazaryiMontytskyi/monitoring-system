package com.nmontytskyi.monitoring.server.controller;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.dto.request.MetricSnapshotRequest;
import com.nmontytskyi.monitoring.server.dto.request.ServiceRegistrationRequest;
import com.nmontytskyi.monitoring.server.dto.response.ServiceResponse;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReportControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("monitoring.polling.enabled", () -> "false");
        registry.add("monitoring.alert.enabled", () -> "false");
        registry.add("monitoring.prediction.enabled", () -> "false");
        registry.add("management.health.mail.enabled", () -> "false");
    }

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private TestRestTemplate restTemplate;

    private Long serviceId;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

        ServiceRegistrationRequest reg = ServiceRegistrationRequest.builder()
                .name("report-it-service-" + System.nanoTime())
                .host("localhost")
                .port(9099)
                .actuatorUrl("http://localhost:9099/actuator")
                .baseUrl("http://localhost:9099")
                .build();
        ResponseEntity<ServiceResponse> resp = restTemplate.postForEntity(
                "/api/services", reg, ServiceResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        serviceId = resp.getBody().getId();
    }

    @Test
    void getSlaReport_withMetricData_returns200AndPdf() {
        pushMetric(HealthStatus.UP, 150);
        pushMetric(HealthStatus.UP, 200);
        pushMetric(HealthStatus.UP, 100);

        String from = LocalDate.now().minusDays(1).toString();
        String to = LocalDate.now().toString();
        ResponseEntity<byte[]> response = restTemplate.getForEntity(
                "/api/reports/{id}/sla?from={from}&to={to}", byte[].class,
                serviceId, from, to);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString())
                .startsWith("application/pdf");
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody()[0]).isEqualTo((byte) 0x25); // '%PDF'
    }

    @Test
    void getSlaReport_serviceNotFound_returns404() {
        String from = LocalDate.now().minusDays(1).toString();
        String to = LocalDate.now().toString();
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/reports/{id}/sla?from={from}&to={to}", String.class,
                99999L, from, to);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getHistory_returnsEmptyList() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/reports/{id}/history", String.class, serviceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("[]");
    }

    @Test
    void getHistory_afterGeneratingReport_containsEntry() {
        pushMetric(HealthStatus.UP, 100);

        String from = LocalDate.now().minusDays(1).toString();
        String to = LocalDate.now().toString();
        restTemplate.getForEntity("/api/reports/{id}/sla?from={from}&to={to}",
                byte[].class, serviceId, from, to);

        ResponseEntity<String> historyResp = restTemplate.getForEntity(
                "/api/reports/{id}/history", String.class, serviceId);

        assertThat(historyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(historyResp.getBody()).contains("SLA_SUMMARY");
    }

    private void pushMetric(HealthStatus status, long responseMs) {
        MetricSnapshotRequest req = MetricSnapshotRequest.builder()
                .serviceId(serviceId)
                .endpoint("GET /test")
                .responseTimeMs(responseMs)
                .status(status)
                .build();
        restTemplate.postForEntity("/api/metrics/endpoint", req, Object.class);
    }
}
