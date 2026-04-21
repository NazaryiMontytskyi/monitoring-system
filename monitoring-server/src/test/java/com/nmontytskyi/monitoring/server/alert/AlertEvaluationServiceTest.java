package com.nmontytskyi.monitoring.server.alert;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.config.AlertProperties;
import com.nmontytskyi.monitoring.server.entity.AlertEventEntity;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity.Comparator;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity.MetricType;
import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import com.nmontytskyi.monitoring.server.repository.AlertEventRepository;
import com.nmontytskyi.monitoring.server.repository.AlertRuleRepository;
import com.nmontytskyi.monitoring.server.repository.MetricRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertEvaluationServiceTest {

    @Mock private AlertProperties alertProperties;
    @Mock private AlertRuleRepository alertRuleRepository;
    @Mock private AlertEventRepository alertEventRepository;
    @Mock private MetricRecordRepository metricRecordRepository;
    @Mock private AlertCooldownManager alertCooldownManager;
    @Mock private AlertNotificationService alertNotificationService;

    @InjectMocks
    private AlertEvaluationService evaluationService;

    private RegisteredServiceEntity service;

    @BeforeEach
    void setUp() {
        service = RegisteredServiceEntity.builder()
                .id(1L)
                .name("test-service")
                .status(HealthStatus.UP)
                .build();
        when(alertProperties.isEnabled()).thenReturn(true);
        when(alertProperties.getEvaluationWindowMinutes()).thenReturn(60);
    }

    @Test
    void evaluate_disabled_skipsAllRules() {
        when(alertProperties.isEnabled()).thenReturn(false);

        evaluationService.evaluate(1L, buildRecord(HealthStatus.DOWN));

        verifyNoInteractions(alertRuleRepository);
        verifyNoInteractions(alertNotificationService);
    }

    @Test
    void evaluate_noRules_doesNothing() {
        when(alertRuleRepository.findAllByServiceIdAndEnabledTrue(1L)).thenReturn(List.of());

        evaluationService.evaluate(1L, buildRecord(HealthStatus.UP));

        verifyNoInteractions(alertNotificationService);
        verify(alertEventRepository, never()).save(any());
    }

    @Test
    void evaluate_statusDown_firesAlert() {
        AlertRuleEntity rule = buildRule(MetricType.STATUS_DOWN, Comparator.GT, 0.5);
        when(alertRuleRepository.findAllByServiceIdAndEnabledTrue(1L)).thenReturn(List.of(rule));
        when(alertCooldownManager.isCooldownExpired(rule)).thenReturn(true);

        evaluationService.evaluate(1L, buildRecord(HealthStatus.DOWN));

        ArgumentCaptor<AlertEventEntity> captor = ArgumentCaptor.forClass(AlertEventEntity.class);
        verify(alertEventRepository).save(captor.capture());
        assertThat(captor.getValue().getMetricValue()).isEqualTo(1.0);
        assertThat(captor.getValue().isNotificationSent()).isTrue();
        verify(alertNotificationService).sendAlert(any(), eq(rule), eq(service));
    }

    @Test
    void evaluate_statusUp_doesNotFire() {
        AlertRuleEntity rule = buildRule(MetricType.STATUS_DOWN, Comparator.GT, 0.5);
        when(alertRuleRepository.findAllByServiceIdAndEnabledTrue(1L)).thenReturn(List.of(rule));

        evaluationService.evaluate(1L, buildRecord(HealthStatus.UP));

        verify(alertEventRepository, never()).save(any());
        verifyNoInteractions(alertNotificationService);
    }

    @Test
    void evaluate_responseTimeExceeded_firesAlert() {
        AlertRuleEntity rule = buildRule(MetricType.RESPONSE_TIME_AVG, Comparator.GT, 500.0);
        when(alertRuleRepository.findAllByServiceIdAndEnabledTrue(1L)).thenReturn(List.of(rule));
        when(metricRecordRepository.avgResponseTimeSince(eq(1L), any(LocalDateTime.class))).thenReturn(750.0);
        when(alertCooldownManager.isCooldownExpired(rule)).thenReturn(true);

        evaluationService.evaluate(1L, buildRecord(HealthStatus.UP));

        verify(alertEventRepository).save(any());
        verify(alertNotificationService).sendAlert(any(), eq(rule), eq(service));
    }

    @Test
    void evaluate_cpuUsageNull_skipsRule() {
        AlertRuleEntity rule = buildRule(MetricType.CPU_USAGE, Comparator.GT, 80.0);
        when(alertRuleRepository.findAllByServiceIdAndEnabledTrue(1L)).thenReturn(List.of(rule));

        MetricRecordEntity record = buildRecord(HealthStatus.UP);
        record.setCpuUsage(null);

        evaluationService.evaluate(1L, record);

        verify(alertEventRepository, never()).save(any());
    }

    @Test
    void evaluate_cooldownActive_suppressesAlert() {
        AlertRuleEntity rule = buildRule(MetricType.STATUS_DOWN, Comparator.GT, 0.5);
        when(alertRuleRepository.findAllByServiceIdAndEnabledTrue(1L)).thenReturn(List.of(rule));
        when(alertCooldownManager.isCooldownExpired(rule)).thenReturn(false);

        evaluationService.evaluate(1L, buildRecord(HealthStatus.DOWN));

        verify(alertEventRepository, never()).save(any());
        verifyNoInteractions(alertNotificationService);
    }

    @Test
    void evaluate_uptimeBelow_firesAlert() {
        AlertRuleEntity rule = buildRule(MetricType.UPTIME_PERCENT, Comparator.LT, 95.0);
        when(alertRuleRepository.findAllByServiceIdAndEnabledTrue(1L)).thenReturn(List.of(rule));
        when(metricRecordRepository.countByServiceIdSince(eq(1L), any())).thenReturn(100L);
        when(metricRecordRepository.countByServiceIdAndStatusSince(eq(1L), any(), eq(HealthStatus.UP))).thenReturn(80L);
        when(alertCooldownManager.isCooldownExpired(rule)).thenReturn(true);

        evaluationService.evaluate(1L, buildRecord(HealthStatus.UP));

        ArgumentCaptor<AlertEventEntity> captor = ArgumentCaptor.forClass(AlertEventEntity.class);
        verify(alertEventRepository).save(captor.capture());
        assertThat(captor.getValue().getMetricValue()).isEqualTo(80.0);
    }

    @Test
    void evaluate_errorRateAbove_firesAlert() {
        AlertRuleEntity rule = buildRule(MetricType.ERROR_RATE, Comparator.GT, 10.0);
        when(alertRuleRepository.findAllByServiceIdAndEnabledTrue(1L)).thenReturn(List.of(rule));
        when(metricRecordRepository.countByServiceIdSince(eq(1L), any())).thenReturn(100L);
        when(metricRecordRepository.countByServiceIdAndStatusSince(eq(1L), any(), eq(HealthStatus.DOWN))).thenReturn(15L);
        when(alertCooldownManager.isCooldownExpired(rule)).thenReturn(true);

        evaluationService.evaluate(1L, buildRecord(HealthStatus.UP));

        ArgumentCaptor<AlertEventEntity> captor = ArgumentCaptor.forClass(AlertEventEntity.class);
        verify(alertEventRepository).save(captor.capture());
        assertThat(captor.getValue().getMetricValue()).isEqualTo(15.0);
    }

    @Test
    void evaluate_responseTimeBelowThreshold_doesNotFire() {
        AlertRuleEntity rule = buildRule(MetricType.RESPONSE_TIME_AVG, Comparator.GT, 500.0);
        when(alertRuleRepository.findAllByServiceIdAndEnabledTrue(1L)).thenReturn(List.of(rule));
        when(metricRecordRepository.avgResponseTimeSince(eq(1L), any(LocalDateTime.class))).thenReturn(200.0);

        evaluationService.evaluate(1L, buildRecord(HealthStatus.UP));

        verify(alertEventRepository, never()).save(any());
    }

    private MetricRecordEntity buildRecord(HealthStatus status) {
        return MetricRecordEntity.builder()
                .service(service)
                .status(status)
                .responseTimeMs(100L)
                .cpuUsage(50.0)
                .build();
    }

    private AlertRuleEntity buildRule(MetricType type, Comparator comparator, double threshold) {
        return AlertRuleEntity.builder()
                .id(10L)
                .service(service)
                .metricType(type)
                .comparator(comparator)
                .threshold(threshold)
                .enabled(true)
                .cooldownMinutes(15)
                .build();
    }
}
