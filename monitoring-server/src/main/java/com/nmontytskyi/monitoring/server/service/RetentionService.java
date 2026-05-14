package com.nmontytskyi.monitoring.server.service;

import com.nmontytskyi.monitoring.server.repository.AlertEventRepository;
import com.nmontytskyi.monitoring.server.repository.MetricRecordRepository;
import com.nmontytskyi.monitoring.server.repository.ReportHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service that enforces the data-retention policy by deleting records older than the
 * configured retention windows.
 *
 * <p>Three retention windows are independently configurable (in days):
 * {@code metric_records}, {@code alert_events}, and {@code report_history}.
 * The actual deletion is triggered by {@link com.nmontytskyi.monitoring.server.scheduler.RetentionScheduler}
 * on a daily or weekly schedule. All settings are read at runtime from
 * {@link com.nmontytskyi.monitoring.server.service.AppSettingsService} so changes
 * take effect without a server restart.
 *
 * @author Nazar Montytskyi
 * @see com.nmontytskyi.monitoring.server.scheduler.RetentionScheduler
 * @see com.nmontytskyi.monitoring.server.service.AppSettingsService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {

    private final AppSettingsService settingsService;
    private final MetricRecordRepository metricRecordRepository;
    private final AlertEventRepository alertEventRepository;
    private final ReportHistoryRepository reportHistoryRepository;

    @Transactional
    public RetentionResult runCleanup() {
        if ("false".equalsIgnoreCase(settingsService.get("retention.enabled", "true"))) {
            return RetentionResult.disabled();
        }

        LocalDateTime metricThreshold = LocalDateTime.now().minusDays(parseLong("retention.metric_records.days", 30));
        LocalDateTime alertThreshold  = LocalDateTime.now().minusDays(parseLong("retention.alert_events.days",   90));
        LocalDateTime reportThreshold = LocalDateTime.now().minusDays(parseLong("retention.report_history.days", 180));

        int deletedMetrics  = metricRecordRepository.deleteByRecordedAtBefore(metricThreshold);
        int deletedAlerts   = alertEventRepository.deleteByFiredAtBefore(alertThreshold);
        int deletedReports  = reportHistoryRepository.deleteByGeneratedAtBefore(reportThreshold);

        RetentionResult result = new RetentionResult(deletedMetrics, deletedAlerts, deletedReports, LocalDateTime.now(), false);
        log.info("Retention cleanup: {} metrics, {} alerts, {} reports deleted", deletedMetrics, deletedAlerts, deletedReports);
        return result;
    }

    private long parseLong(String key, long defaultVal) {
        try {
            return Long.parseLong(settingsService.get(key, String.valueOf(defaultVal)));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public record RetentionResult(
            int deletedMetrics,
            int deletedAlerts,
            int deletedReports,
            LocalDateTime ranAt,
            boolean skipped
    ) {
        static RetentionResult disabled() {
            return new RetentionResult(0, 0, 0, LocalDateTime.now(), true);
        }
    }
}
