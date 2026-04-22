package com.nmontytskyi.monitoring.server.web;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.dto.request.MetricSnapshotRequest;
import com.nmontytskyi.monitoring.server.dto.request.ServiceRegistrationRequest;
import com.nmontytskyi.monitoring.server.dto.response.AlertRuleResponse;
import com.nmontytskyi.monitoring.server.dto.response.ServiceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MvcControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("management.health.mail.enabled", () -> "false");
    }

    @MockBean
    JavaMailSender javaMailSender;

    @Autowired
    TestRestTemplate restTemplate;

    // ── 1 ──────────────────────────────────────────────────────────────────

    @Test
    void dashboard_withRegisteredService_showsItInHtml() {
        registerService("it-dash-service");

        ResponseEntity<String> resp = restTemplate.getForEntity("/", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("it-dash-service");
    }

    // ── 2 ──────────────────────────────────────────────────────────────────

    @Test
    void serviceDetail_afterPushingMetrics_showsAggregateData() {
        ServiceResponse svc = registerService("it-detail-service");
        Long id = svc.getId();

        for (int i = 0; i < 3; i++) {
            MetricSnapshotRequest metric = MetricSnapshotRequest.builder()
                    .serviceId(id)
                    .endpoint("GET /test")
                    .responseTimeMs(200L + i * 50L)
                    .status(HealthStatus.UP)
                    .build();
            restTemplate.postForEntity("/api/metrics/endpoint", metric, String.class);
        }

        ResponseEntity<String> resp = restTemplate.getForEntity("/services/" + id, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("ms");
    }

    // ── 3 ──────────────────────────────────────────────────────────────────

    @Test
    void slaReport_forExistingService_returns200() {
        ServiceResponse svc = registerService("it-sla-service");

        ResponseEntity<String> resp = restTemplate.getForEntity(
                "/services/" + svc.getId() + "/sla", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── 4 ──────────────────────────────────────────────────────────────────

    @Test
    void alerts_createAndDeleteRule_roundTrip() {
        ServiceResponse svc = registerService("it-alert-service");
        Long serviceId = svc.getId();

        // Create rule via form POST
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("serviceId",       serviceId.toString());
        form.add("metricType",      "RESPONSE_TIME_AVG");
        form.add("comparator",      "GT");
        form.add("threshold",       "500");
        form.add("cooldownMinutes", "15");
        form.add("enabled",         "true");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

        ResponseEntity<String> createResp = restTemplate.postForEntity("/alerts/rules", entity, String.class);
        assertThat(createResp.getStatusCode().is3xxRedirection()).isTrue();

        // Alerts page should contain the metric type
        ResponseEntity<String> alertsPage = restTemplate.getForEntity("/alerts", String.class);
        assertThat(alertsPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(alertsPage.getBody()).contains("RESPONSE_TIME_AVG");

        // Fetch rule id via API
        AlertRuleResponse[] rules = restTemplate
                .getForEntity("/api/alerts/rules?serviceId=" + serviceId, AlertRuleResponse[].class)
                .getBody();
        assertThat(rules).isNotNull().isNotEmpty();
        Long ruleId = rules[0].getId();

        // Delete rule via form POST
        ResponseEntity<String> deleteResp = restTemplate.postForEntity(
                "/alerts/rules/" + ruleId + "/delete", null, String.class);
        assertThat(deleteResp.getStatusCode().is3xxRedirection()).isTrue();

        // Rule should no longer appear in the alerts page
        ResponseEntity<String> afterDelete = restTemplate.getForEntity("/alerts", String.class);
        AlertRuleResponse[] rulesAfter = restTemplate
                .getForEntity("/api/alerts/rules?serviceId=" + serviceId, AlertRuleResponse[].class)
                .getBody();
        assertThat(rulesAfter).isNotNull().isEmpty();
    }

    // ── 5 ──────────────────────────────────────────────────────────────────

    @Test
    void unknownServiceId_returns404Page() {
        ResponseEntity<String> resp = restTemplate.getForEntity("/services/999999", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private ServiceResponse registerService(String name) {
        ServiceRegistrationRequest req = ServiceRegistrationRequest.builder()
                .name(name)
                .host("localhost")
                .port(9090)
                .actuatorUrl("http://localhost:9090/actuator")
                .baseUrl("http://localhost:9090")
                .build();
        ResponseEntity<ServiceResponse> resp = restTemplate.postForEntity(
                "/api/services", req, ServiceResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody();
    }
}
