package com.nmontytskyi.monitoring.server;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.dto.request.MetricSnapshotRequest;
import com.nmontytskyi.monitoring.server.dto.request.ServiceRegistrationRequest;
import com.nmontytskyi.monitoring.server.dto.response.AggregateMetricsResponse;
import com.nmontytskyi.monitoring.server.dto.response.DashboardSummaryResponse;
import com.nmontytskyi.monitoring.server.dto.response.ServiceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MonitoringApiIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void fullScenario_registerPushMetricAggregateDashboard() {
        // 1. Register a service
        ServiceRegistrationRequest regReq = ServiceRegistrationRequest.builder()
                .name("it-test-service")
                .host("localhost")
                .port(9090)
                .actuatorUrl("http://localhost:9090/actuator")
                .baseUrl("http://localhost:9090")
                .build();

        ResponseEntity<ServiceResponse> regResp = restTemplate.postForEntity(
                "/api/services", regReq, ServiceResponse.class);

        assertThat(regResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(regResp.getBody()).isNotNull();
        Long serviceId = regResp.getBody().getId();
        assertThat(serviceId).isNotNull();
        assertThat(regResp.getHeaders().getLocation()).isNotNull();
        assertThat(regResp.getHeaders().getLocation().toString()).contains("/api/services/" + serviceId);

        // 2. Push a metric snapshot
        MetricSnapshotRequest metricReq = MetricSnapshotRequest.builder()
                .serviceId(serviceId)
                .endpoint("GET /items")
                .responseTimeMs(300L)
                .status(HealthStatus.UP)
                .build();

        ResponseEntity<String> pushResp = restTemplate.postForEntity(
                "/api/metrics/endpoint", metricReq, String.class);
        assertThat(pushResp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // 3. Get aggregate metrics
        LocalDateTime from = LocalDateTime.now().minusHours(1);
        LocalDateTime to = LocalDateTime.now().plusMinutes(1);
        String aggUrl = "/api/metrics/" + serviceId + "/aggregate?from=" +
                from.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) +
                "&to=" + to.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        ResponseEntity<AggregateMetricsResponse> aggResp = restTemplate.getForEntity(
                aggUrl, AggregateMetricsResponse.class);
        assertThat(aggResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aggResp.getBody()).isNotNull();
        assertThat(aggResp.getBody().getTotalRequests()).isEqualTo(1);
        assertThat(aggResp.getBody().getAvgResponseTimeMs()).isEqualTo(300.0);

        // 4. Check dashboard contains the registered service
        ResponseEntity<DashboardSummaryResponse> dashResp = restTemplate.getForEntity(
                "/api/dashboard", DashboardSummaryResponse.class);
        assertThat(dashResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dashResp.getBody()).isNotNull();
        assertThat(dashResp.getBody().getTotalServices()).isGreaterThanOrEqualTo(1);
        assertThat(dashResp.getBody().getServices())
                .anyMatch(s -> "it-test-service".equals(s.getName()));
    }
}
