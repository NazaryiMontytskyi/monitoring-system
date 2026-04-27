package com.nmontytskyi.monitoring.server.controller;

import com.nmontytskyi.monitoring.model.HealthStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MetricsTimeSeriesControllerIT {

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
                .name("ts-it-service-" + System.nanoTime())
                .host("localhost")
                .port(9088)
                .actuatorUrl("http://localhost:9088/actuator")
                .baseUrl("http://localhost:9088")
                .build();
        ResponseEntity<ServiceResponse> resp = restTemplate.postForEntity(
                "/api/services", reg, ServiceResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        serviceId = resp.getBody().getId();
    }

    @Test
    void getServiceHistory_noRecords_returns200WithEmptyArray() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/metrics/{id}/history", String.class, serviceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("[]");
    }

    @Test
    void getSystemHistory_returns200() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/metrics/system/history", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }
}
