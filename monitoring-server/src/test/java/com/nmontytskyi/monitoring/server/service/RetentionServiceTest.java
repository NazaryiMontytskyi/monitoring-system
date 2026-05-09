package com.nmontytskyi.monitoring.server.service;

import com.nmontytskyi.monitoring.server.repository.AlertEventRepository;
import com.nmontytskyi.monitoring.server.repository.MetricRecordRepository;
import com.nmontytskyi.monitoring.server.repository.ReportHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetentionServiceTest {

    @Mock private AppSettingsService settingsService;
    @Mock private MetricRecordRepository metricRecordRepository;
    @Mock private AlertEventRepository alertEventRepository;
    @Mock private ReportHistoryRepository reportHistoryRepository;

    @InjectMocks private RetentionService retentionService;

    @Test
    void runCleanup_whenDisabled_returnsSkippedResult() {
        when(settingsService.get("retention.enabled", "true")).thenReturn("false");

        RetentionService.RetentionResult result = retentionService.runCleanup();

        assertThat(result.skipped()).isTrue();
        assertThat(result.deletedMetrics()).isEqualTo(0);
        assertThat(result.deletedAlerts()).isEqualTo(0);
        assertThat(result.deletedReports()).isEqualTo(0);
        verifyNoInteractions(metricRecordRepository, alertEventRepository, reportHistoryRepository);
    }

    @Test
    void runCleanup_whenEnabled_deletesFromAllRepositories() {
        when(settingsService.get("retention.enabled", "true")).thenReturn("true");
        when(settingsService.get("retention.metric_records.days", "30")).thenReturn("30");
        when(settingsService.get("retention.alert_events.days", "90")).thenReturn("90");
        when(settingsService.get("retention.report_history.days", "180")).thenReturn("180");
        when(metricRecordRepository.deleteByRecordedAtBefore(any())).thenReturn(10);
        when(alertEventRepository.deleteByFiredAtBefore(any())).thenReturn(5);
        when(reportHistoryRepository.deleteByGeneratedAtBefore(any())).thenReturn(2);

        RetentionService.RetentionResult result = retentionService.runCleanup();

        assertThat(result.skipped()).isFalse();
        assertThat(result.deletedMetrics()).isEqualTo(10);
        assertThat(result.deletedAlerts()).isEqualTo(5);
        assertThat(result.deletedReports()).isEqualTo(2);
        assertThat(result.ranAt()).isNotNull();
    }

    @Test
    void runCleanup_usesCorrectThresholdDates() {
        when(settingsService.get("retention.enabled", "true")).thenReturn("true");
        when(settingsService.get("retention.metric_records.days", "30")).thenReturn("7");
        when(settingsService.get("retention.alert_events.days", "90")).thenReturn("14");
        when(settingsService.get("retention.report_history.days", "180")).thenReturn("30");
        when(metricRecordRepository.deleteByRecordedAtBefore(any())).thenReturn(0);
        when(alertEventRepository.deleteByFiredAtBefore(any())).thenReturn(0);
        when(reportHistoryRepository.deleteByGeneratedAtBefore(any())).thenReturn(0);

        LocalDateTime before = LocalDateTime.now();
        retentionService.runCleanup();
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> metricsCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> alertsCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> reportsCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(metricRecordRepository).deleteByRecordedAtBefore(metricsCaptor.capture());
        verify(alertEventRepository).deleteByFiredAtBefore(alertsCaptor.capture());
        verify(reportHistoryRepository).deleteByGeneratedAtBefore(reportsCaptor.capture());

        assertThat(metricsCaptor.getValue()).isBetween(before.minusDays(7), after.minusDays(7).plusSeconds(1));
        assertThat(alertsCaptor.getValue()).isBetween(before.minusDays(14), after.minusDays(14).plusSeconds(1));
        assertThat(reportsCaptor.getValue()).isBetween(before.minusDays(30), after.minusDays(30).plusSeconds(1));
    }

    @Test
    void runCleanup_invalidDaysValue_usesDefault() {
        when(settingsService.get("retention.enabled", "true")).thenReturn("true");
        when(settingsService.get("retention.metric_records.days", "30")).thenReturn("not-a-number");
        when(settingsService.get("retention.alert_events.days", "90")).thenReturn("90");
        when(settingsService.get("retention.report_history.days", "180")).thenReturn("180");
        when(metricRecordRepository.deleteByRecordedAtBefore(any())).thenReturn(0);
        when(alertEventRepository.deleteByFiredAtBefore(any())).thenReturn(0);
        when(reportHistoryRepository.deleteByGeneratedAtBefore(any())).thenReturn(0);

        LocalDateTime before = LocalDateTime.now();
        retentionService.runCleanup();
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(metricRecordRepository).deleteByRecordedAtBefore(captor.capture());

        // should fall back to default of 30 days
        assertThat(captor.getValue()).isBetween(before.minusDays(30), after.minusDays(30).plusSeconds(1));
    }

    @Test
    void runCleanup_falseStringCaseInsensitive_skips() {
        when(settingsService.get("retention.enabled", "true")).thenReturn("FALSE");

        RetentionService.RetentionResult result = retentionService.runCleanup();

        assertThat(result.skipped()).isTrue();
        verifyNoInteractions(metricRecordRepository);
    }

    @Test
    void runCleanup_nothingToDelete_returnsZeroCounts() {
        when(settingsService.get("retention.enabled", "true")).thenReturn("true");
        when(settingsService.get("retention.metric_records.days", "30")).thenReturn("30");
        when(settingsService.get("retention.alert_events.days", "90")).thenReturn("90");
        when(settingsService.get("retention.report_history.days", "180")).thenReturn("180");
        when(metricRecordRepository.deleteByRecordedAtBefore(any())).thenReturn(0);
        when(alertEventRepository.deleteByFiredAtBefore(any())).thenReturn(0);
        when(reportHistoryRepository.deleteByGeneratedAtBefore(any())).thenReturn(0);

        RetentionService.RetentionResult result = retentionService.runCleanup();

        assertThat(result.skipped()).isFalse();
        assertThat(result.deletedMetrics()).isZero();
        assertThat(result.deletedAlerts()).isZero();
        assertThat(result.deletedReports()).isZero();
    }
}
