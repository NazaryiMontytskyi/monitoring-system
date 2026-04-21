package com.nmontytskyi.monitoring.server.alert;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.dto.request.MetricSnapshotRequest;
import com.nmontytskyi.monitoring.server.entity.AlertEventEntity;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity.Comparator;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity.MetricType;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import com.nmontytskyi.monitoring.server.repository.AlertEventRepository;
import com.nmontytskyi.monitoring.server.repository.AlertRuleRepository;
import com.nmontytskyi.monitoring.server.repository.RegisteredServiceRepository;
import com.nmontytskyi.monitoring.server.service.MetricsPersistenceService;
import jakarta.mail.internet.MimeMessage;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AlertEvaluationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("monitoring.alert.enabled", () -> "true");
        registry.add("monitoring.alert.notification-to", () -> "admin@example.com");
        registry.add("monitoring.polling.enabled", () -> "false");
        registry.add("management.health.mail.enabled", () -> "false");
    }

    @MockBean
    private JavaMailSender javaMailSender;

    @Autowired private MetricsPersistenceService metricsPersistenceService;
    @Autowired private AlertEventRepository alertEventRepository;
    @Autowired private AlertRuleRepository alertRuleRepository;
    @Autowired private RegisteredServiceRepository serviceRepository;

    private RegisteredServiceEntity service;

    @BeforeEach
    void setUp() {
        alertEventRepository.deleteAll();
        alertRuleRepository.deleteAll();
        serviceRepository.deleteAll();

        service = serviceRepository.save(RegisteredServiceEntity.builder()
                .name("it-alert-service")
                .host("localhost")
                .port(9090)
                .actuatorUrl("http://localhost:9090/actuator")
                .baseUrl("http://localhost:9090")
                .status(HealthStatus.UP)
                .build());

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    void evaluate_whenRuleViolated_savesAlertEvent() {
        AlertRuleEntity rule = alertRuleRepository.save(AlertRuleEntity.builder()
                .service(service)
                .metricType(MetricType.STATUS_DOWN)
                .comparator(Comparator.GT)
                .threshold(0.5)
                .enabled(true)
                .cooldownMinutes(15)
                .build());

        MetricSnapshotRequest request = MetricSnapshotRequest.builder()
                .serviceId(service.getId())
                .endpoint("/api/test")
                .responseTimeMs(100L)
                .status(HealthStatus.DOWN)
                .build();

        metricsPersistenceService.saveEndpointSnapshot(request);

        List<AlertEventEntity> events = alertEventRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getMetricValue()).isEqualTo(1.0);
        assertThat(events.get(0).getRule().getId()).isEqualTo(rule.getId());
        assertThat(events.get(0).isNotificationSent()).isTrue();

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void evaluate_cooldown_preventsDoubleAlert() {
        alertRuleRepository.save(AlertRuleEntity.builder()
                .service(service)
                .metricType(MetricType.STATUS_DOWN)
                .comparator(Comparator.GT)
                .threshold(0.5)
                .enabled(true)
                .cooldownMinutes(15)
                .build());

        MetricSnapshotRequest request = MetricSnapshotRequest.builder()
                .serviceId(service.getId())
                .endpoint("/api/test")
                .responseTimeMs(100L)
                .status(HealthStatus.DOWN)
                .build();

        metricsPersistenceService.saveEndpointSnapshot(request);
        metricsPersistenceService.saveEndpointSnapshot(request);

        assertThat(alertEventRepository.count()).isEqualTo(1);
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void evaluate_whenRuleDisabled_doesNotFire() {
        alertRuleRepository.save(AlertRuleEntity.builder()
                .service(service)
                .metricType(MetricType.STATUS_DOWN)
                .comparator(Comparator.GT)
                .threshold(0.5)
                .enabled(false)
                .cooldownMinutes(15)
                .build());

        MetricSnapshotRequest request = MetricSnapshotRequest.builder()
                .serviceId(service.getId())
                .endpoint("/api/test")
                .responseTimeMs(100L)
                .status(HealthStatus.DOWN)
                .build();

        metricsPersistenceService.saveEndpointSnapshot(request);

        assertThat(alertEventRepository.count()).isEqualTo(0);
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }
}
