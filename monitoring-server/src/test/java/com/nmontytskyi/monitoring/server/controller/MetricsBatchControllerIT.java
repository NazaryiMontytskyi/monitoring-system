package com.nmontytskyi.monitoring.server.controller;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.dto.request.MetricSnapshotRequest;
import com.nmontytskyi.monitoring.server.dto.request.ServiceRegistrationRequest;
import com.nmontytskyi.monitoring.server.dto.response.MetricRecordResponse;
import com.nmontytskyi.monitoring.server.dto.response.ServiceResponse;
import com.nmontytskyi.monitoring.server.repository.MetricRecordRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MetricsBatchControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("monitoring.polling.enabled", () -> "false");
        registry.add("monitoring.alert.enabled", () -> "false");
        registry.add("management.health.mail.enabled", () -> "false");
    }

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MetricRecordRepository metricRecordRepository;

    private Long serviceId;

    @BeforeEach
    void registerService() {
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

        ServiceRegistrationRequest reg = ServiceRegistrationRequest.builder()
                .name("batch-it-service-" + System.nanoTime())
                .host("localhost")
                .port(9091)
                .actuatorUrl("http://localhost:9091/actuator")
                .baseUrl("http://localhost:9091")
                .build();
        ResponseEntity<ServiceResponse> resp = restTemplate.postForEntity(
                "/api/services", reg, ServiceResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        serviceId = resp.getBody().getId();
    }

    @Test
    void postBatch_savesAllRecords() {
        long countBefore = metricRecordRepository.count();

        List<MetricSnapshotRequest> batch = List.of(
                buildRequest("GET /orders", HealthStatus.UP, 100),
                buildRequest("POST /orders", HealthStatus.UP, 200),
                buildRequest("GET /orders/1", HealthStatus.DOWN, 50)
        );

        ResponseEntity<List<MetricRecordResponse>> resp = restTemplate.exchange(
                "/api/metrics/batch",
                POST,
                new org.springframework.http.HttpEntity<>(batch),
                new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resp.getBody()).hasSize(3);
        assertThat(metricRecordRepository.count()).isEqualTo(countBefore + 3);
    }

    @Test
    void postBatch_responseContainsCorrectSources() {
        List<MetricSnapshotRequest> batch = List.of(
                buildRequest("GET /items", HealthStatus.UP, 150));

        ResponseEntity<List<MetricRecordResponse>> resp = restTemplate.exchange(
                "/api/metrics/batch",
                POST,
                new org.springframework.http.HttpEntity<>(batch),
                new ParameterizedTypeReference<>() {});

        assertThat(resp.getBody()).allMatch(r -> "PUSH".equals(r.getSource()));
    }

    @Test
    void postBatch_emptyList_returns202() {
        ResponseEntity<List<MetricRecordResponse>> resp = restTemplate.exchange(
                "/api/metrics/batch",
                POST,
                new org.springframework.http.HttpEntity<>(List.of()),
                new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resp.getBody()).isEmpty();
    }

    @Test
    void postBatch_invalidPayload_returns400() {
        String invalidJson = "[{\"serviceId\": null, \"endpoint\": \"\", \"status\": null}]";

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/api/metrics/batch",
                new org.springframework.http.HttpEntity<>(invalidJson, headers),
                String.class);

        // null serviceId, blank endpoint, null status all violate @NotNull/@NotBlank
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    private MetricSnapshotRequest buildRequest(String endpoint, HealthStatus status, long responseMs) {
        return MetricSnapshotRequest.builder()
                .serviceId(serviceId)
                .endpoint(endpoint)
                .responseTimeMs(responseMs)
                .status(status)
                .build();
    }
}
