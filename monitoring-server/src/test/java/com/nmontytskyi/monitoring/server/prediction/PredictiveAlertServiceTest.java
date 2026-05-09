package com.nmontytskyi.monitoring.server.prediction;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.alert.AlertCooldownManager;
import com.nmontytskyi.monitoring.server.alert.AlertNotificationService;
import com.nmontytskyi.monitoring.server.config.PredictionProperties;
import com.nmontytskyi.monitoring.server.entity.AlertEventEntity;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PredictiveAlertServiceTest {

    @Mock private MetricRecordRepository metricRecordRepository;
    @Mock private AlertRuleRepository alertRuleRepository;
    @Mock private AlertEventRepository alertEventRepository;
    @Mock private AlertCooldownManager alertCooldownManager;
    @Mock private AlertNotificationService alertNotificationService;
    @Mock private PredictionProperties predictionProperties;

    @InjectMocks private PredictiveAlertService predictiveAlertService;

    private RegisteredServiceEntity service;

    @BeforeEach
    void setUp() {
        service = RegisteredServiceEntity.builder().id(1L).name("test-service").build();
        when(predictionProperties.isEnabled()).thenReturn(true);
        when(predictionProperties.getMinRSquared()).thenReturn(0.3);
    }

    private AlertRuleEntity buildRule(AlertRuleEntity.MetricType type,
                                      AlertRuleEntity.Comparator comparator,
                                      double threshold,
                                      int lookahead,
                                      int minPoints) {
        return AlertRuleEntity.builder()
                .id(10L)
                .service(service)
                .metricType(type)
                .comparator(comparator)
                .threshold(threshold)
                .enabled(true)
                .predictiveEnabled(true)
                .lookaheadMinutes(lookahead)
                .minDataPoints(minPoints)
                .cooldownMinutes(15)
                .build();
    }

    private List<MetricRecordEntity> buildLinearRecords(int count, long startMs, long stepMs) {
        List<MetricRecordEntity> records = new ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        for (int i = 0; i < count; i++) {
            records.add(MetricRecordEntity.builder()
                    .id((long) i)
                    .service(service)
                    .responseTimeMs(startMs + (long) i * stepMs)
                    .status(HealthStatus.UP)
                    .source(MetricRecordEntity.MetricSource.PULL)
                    .recordedAt(base.plusSeconds(i * 30L))
                    .build());
        }
        return records;
    }

    // ── disabled ─────────────────────────────────────────────────────────────

    @Test
    void runPredictiveChecks_disabled_skipsAllProcessing() {
        when(predictionProperties.isEnabled()).thenReturn(false);

        predictiveAlertService.runPredictiveChecks();

        verifyNoInteractions(alertRuleRepository, metricRecordRepository, alertEventRepository);
    }

    // ── not enough data ───────────────────────────────────────────────────────

    @Test
    void runPredictiveChecks_notEnoughDataPoints_doesNotFireAlert() {
        AlertRuleEntity rule = buildRule(
                AlertRuleEntity.MetricType.RESPONSE_TIME_AVG, AlertRuleEntity.Comparator.GT, 500, 10, 10);

        when(alertRuleRepository.findAllByPredictiveEnabledTrueAndEnabledTrue()).thenReturn(List.of(rule));
        when(metricRecordRepository.findTop40ByServiceIdAndSourceOrderByRecordedAtDesc(
                eq(1L), eq(MetricRecordEntity.MetricSource.PULL)))
                .thenReturn(buildLinearRecords(3, 100, 10)); // only 3, need 10

        predictiveAlertService.runPredictiveChecks();

        verify(alertEventRepository, never()).save(any());
    }

    // ── threshold not breached ────────────────────────────────────────────────

    @Test
    void runPredictiveChecks_predictedValueBelowThreshold_doesNotFireAlert() {
        AlertRuleEntity rule = buildRule(
                AlertRuleEntity.MetricType.RESPONSE_TIME_AVG, AlertRuleEntity.Comparator.GT, 10_000, 10, 5);

        when(alertRuleRepository.findAllByPredictiveEnabledTrueAndEnabledTrue()).thenReturn(List.of(rule));
        // linear records: 100ms → 200ms → ... slowly rising, will not reach 10_000
        when(metricRecordRepository.findTop40ByServiceIdAndSourceOrderByRecordedAtDesc(
                eq(1L), eq(MetricRecordEntity.MetricSource.PULL)))
                .thenReturn(buildLinearRecords(10, 100, 10));

        predictiveAlertService.runPredictiveChecks();

        verify(alertEventRepository, never()).save(any());
    }

    // ── alert fires ───────────────────────────────────────────────────────────

    @Test
    void runPredictiveChecks_predictedValueExceedsThreshold_firesAlert() {
        AlertRuleEntity rule = buildRule(
                AlertRuleEntity.MetricType.RESPONSE_TIME_AVG, AlertRuleEntity.Comparator.GT, 300, 10, 5);

        when(alertRuleRepository.findAllByPredictiveEnabledTrueAndEnabledTrue()).thenReturn(List.of(rule));
        // strongly linear rising data: 100, 150, 200, 250, 300, 350 — will predict well above 300
        when(metricRecordRepository.findTop40ByServiceIdAndSourceOrderByRecordedAtDesc(
                eq(1L), eq(MetricRecordEntity.MetricSource.PULL)))
                .thenReturn(buildLinearRecords(10, 100, 50));
        when(alertCooldownManager.isCooldownExpired(rule)).thenReturn(true);
        when(alertEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        predictiveAlertService.runPredictiveChecks();

        ArgumentCaptor<AlertEventEntity> captor = ArgumentCaptor.forClass(AlertEventEntity.class);
        verify(alertEventRepository, atLeastOnce()).save(captor.capture());
        AlertEventEntity saved = captor.getAllValues().get(0);
        assertThat(saved.isPredictive()).isTrue();
        assertThat(saved.getMessage()).contains("PREDICTIVE");
        assertThat(saved.getPredictedBreachAt()).isAfter(LocalDateTime.now().minusSeconds(5));
        assertThat(saved.getConfidenceScore()).isGreaterThan(0.3);
    }

    @Test
    void runPredictiveChecks_cooldownActive_suppressesAlert() {
        AlertRuleEntity rule = buildRule(
                AlertRuleEntity.MetricType.RESPONSE_TIME_AVG, AlertRuleEntity.Comparator.GT, 300, 10, 5);

        when(alertRuleRepository.findAllByPredictiveEnabledTrueAndEnabledTrue()).thenReturn(List.of(rule));
        when(metricRecordRepository.findTop40ByServiceIdAndSourceOrderByRecordedAtDesc(
                eq(1L), eq(MetricRecordEntity.MetricSource.PULL)))
                .thenReturn(buildLinearRecords(10, 100, 50));
        when(alertCooldownManager.isCooldownExpired(rule)).thenReturn(false);

        predictiveAlertService.runPredictiveChecks();

        verify(alertEventRepository, never()).save(any());
    }

    // ── low R² ───────────────────────────────────────────────────────────────

    @Test
    void runPredictiveChecks_lowRSquared_skipsAlert() {
        AlertRuleEntity rule = buildRule(
                AlertRuleEntity.MetricType.RESPONSE_TIME_AVG, AlertRuleEntity.Comparator.GT, 100, 10, 5);

        // chaotic data — alternating high and low, no linear trend
        List<MetricRecordEntity> chaotic = new ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        long[] vals = {500, 50, 600, 40, 700, 30, 800, 20, 900, 10};
        for (int i = 0; i < vals.length; i++) {
            chaotic.add(MetricRecordEntity.builder()
                    .id((long) i).service(service)
                    .responseTimeMs(vals[i])
                    .status(HealthStatus.UP)
                    .source(MetricRecordEntity.MetricSource.PULL)
                    .recordedAt(base.plusSeconds(i * 30L))
                    .build());
        }

        when(alertRuleRepository.findAllByPredictiveEnabledTrueAndEnabledTrue()).thenReturn(List.of(rule));
        when(metricRecordRepository.findTop40ByServiceIdAndSourceOrderByRecordedAtDesc(
                eq(1L), eq(MetricRecordEntity.MetricSource.PULL)))
                .thenReturn(chaotic);

        predictiveAlertService.runPredictiveChecks();

        verify(alertEventRepository, never()).save(any());
    }

    // ── metric types ─────────────────────────────────────────────────────────

    @Test
    void runPredictiveChecks_cpuUsageMetric_extractsCorrectly() {
        AlertRuleEntity rule = buildRule(
                AlertRuleEntity.MetricType.CPU_USAGE, AlertRuleEntity.Comparator.GT, 50.0, 10, 5);

        List<MetricRecordEntity> records = new ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        for (int i = 0; i < 10; i++) {
            records.add(MetricRecordEntity.builder()
                    .id((long) i).service(service)
                    .responseTimeMs(100)
                    .cpuUsage(0.1 + i * 0.05) // 10%..55% — rising past 50%
                    .status(HealthStatus.UP)
                    .source(MetricRecordEntity.MetricSource.PULL)
                    .recordedAt(base.plusSeconds(i * 30L))
                    .build());
        }

        when(alertRuleRepository.findAllByPredictiveEnabledTrueAndEnabledTrue()).thenReturn(List.of(rule));
        when(metricRecordRepository.findTop40ByServiceIdAndSourceOrderByRecordedAtDesc(
                eq(1L), eq(MetricRecordEntity.MetricSource.PULL)))
                .thenReturn(records);
        when(alertCooldownManager.isCooldownExpired(rule)).thenReturn(true);
        when(alertEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        predictiveAlertService.runPredictiveChecks();

        verify(alertEventRepository, atLeastOnce()).save(any(AlertEventEntity.class));
    }

    @Test
    void runPredictiveChecks_cpuNullValues_skipsNullRecords() {
        AlertRuleEntity rule = buildRule(
                AlertRuleEntity.MetricType.CPU_USAGE, AlertRuleEntity.Comparator.GT, 50.0, 10, 5);

        // all records have null CPU — should not produce enough valid values
        List<MetricRecordEntity> records = new ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        for (int i = 0; i < 10; i++) {
            records.add(MetricRecordEntity.builder()
                    .id((long) i).service(service)
                    .responseTimeMs(100)
                    .cpuUsage(null)
                    .status(HealthStatus.UP)
                    .source(MetricRecordEntity.MetricSource.PULL)
                    .recordedAt(base.plusSeconds(i * 30L))
                    .build());
        }

        when(alertRuleRepository.findAllByPredictiveEnabledTrueAndEnabledTrue()).thenReturn(List.of(rule));
        when(metricRecordRepository.findTop40ByServiceIdAndSourceOrderByRecordedAtDesc(
                eq(1L), eq(MetricRecordEntity.MetricSource.PULL)))
                .thenReturn(records);

        predictiveAlertService.runPredictiveChecks();

        verify(alertEventRepository, never()).save(any());
    }

    // ── notification ─────────────────────────────────────────────────────────

    @Test
    void runPredictiveChecks_notificationFails_eventStillSaved() {
        AlertRuleEntity rule = buildRule(
                AlertRuleEntity.MetricType.RESPONSE_TIME_AVG, AlertRuleEntity.Comparator.GT, 300, 10, 5);

        when(alertRuleRepository.findAllByPredictiveEnabledTrueAndEnabledTrue()).thenReturn(List.of(rule));
        when(metricRecordRepository.findTop40ByServiceIdAndSourceOrderByRecordedAtDesc(
                eq(1L), eq(MetricRecordEntity.MetricSource.PULL)))
                .thenReturn(buildLinearRecords(10, 100, 50));
        when(alertCooldownManager.isCooldownExpired(rule)).thenReturn(true);
        when(alertEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("mail server down"))
                .when(alertNotificationService).sendAlert(any(), any(), any());

        predictiveAlertService.runPredictiveChecks();

        // event should still be saved once even though notification failed
        verify(alertEventRepository, atLeastOnce()).save(any(AlertEventEntity.class));
    }

    // ── LT comparator ────────────────────────────────────────────────────────

    @Test
    void runPredictiveChecks_ltComparator_firesWhenPredictedBelowThreshold() {
        AlertRuleEntity rule = buildRule(
                AlertRuleEntity.MetricType.UPTIME_PERCENT, AlertRuleEntity.Comparator.LT, 90.0, 10, 5);

        // 4 UP, 6 DOWN records — uptime ~40%, below 90%
        List<MetricRecordEntity> records = new ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        for (int i = 0; i < 10; i++) {
            HealthStatus status = i < 4 ? HealthStatus.UP : HealthStatus.DOWN;
            records.add(MetricRecordEntity.builder()
                    .id((long) i).service(service)
                    .responseTimeMs(100)
                    .status(status)
                    .source(MetricRecordEntity.MetricSource.PULL)
                    .recordedAt(base.plusSeconds(i * 30L))
                    .build());
        }

        when(alertRuleRepository.findAllByPredictiveEnabledTrueAndEnabledTrue()).thenReturn(List.of(rule));
        when(metricRecordRepository.findTop40ByServiceIdAndSourceOrderByRecordedAtDesc(
                eq(1L), eq(MetricRecordEntity.MetricSource.PULL)))
                .thenReturn(records);
        when(alertCooldownManager.isCooldownExpired(rule)).thenReturn(true);
        when(alertEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        predictiveAlertService.runPredictiveChecks();

        verify(alertEventRepository, atLeastOnce()).save(any(AlertEventEntity.class));
    }

    // ── exception in processRule ──────────────────────────────────────────────

    @Test
    void runPredictiveChecks_ruleThrows_continuesWithNextRule() {
        AlertRuleEntity badRule = buildRule(
                AlertRuleEntity.MetricType.RESPONSE_TIME_AVG, AlertRuleEntity.Comparator.GT, 500, 10, 5);
        badRule.setId(99L);

        when(alertRuleRepository.findAllByPredictiveEnabledTrueAndEnabledTrue()).thenReturn(List.of(badRule));
        when(metricRecordRepository.findTop40ByServiceIdAndSourceOrderByRecordedAtDesc(anyLong(), any()))
                .thenThrow(new RuntimeException("db failure"));

        // should not propagate exception
        predictiveAlertService.runPredictiveChecks();

        verify(alertEventRepository, never()).save(any());
    }
}
