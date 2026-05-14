package com.nmontytskyi.monitoring.server.scheduler;

import com.nmontytskyi.monitoring.server.service.AppSettingsService;
import com.nmontytskyi.monitoring.server.service.RetentionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledFuture;

/**
 * Scheduled component that triggers periodic database cleanup according to the
 * configured data-retention policy.
 *
 * <p>Reads the {@code retention.enabled}, {@code retention.frequency}, and
 * {@code retention.time} settings at runtime from
 * {@link com.nmontytskyi.monitoring.server.service.AppSettingsService} and delegates
 * the actual deletion to {@link com.nmontytskyi.monitoring.server.service.RetentionService}.
 * Running daily by default at 03:00, the scheduler removes metric records, alert events,
 * and report history entries older than their respective retention windows.
 *
 * @author Nazar Montytskyi
 * @see com.nmontytskyi.monitoring.server.service.RetentionService
 * @see com.nmontytskyi.monitoring.server.service.AppSettingsService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionScheduler {

    private final RetentionService retentionService;
    private final AppSettingsService settingsService;
    private final TaskScheduler taskScheduler;

    private ScheduledFuture<?> currentTask;

    @PostConstruct
    public void scheduleFromSettings() {
        reschedule();
    }

    public void reschedule() {
        if (currentTask != null) {
            currentTask.cancel(false);
        }
        String cron = settingsService.get("retention.cron", "0 0 3 * * *");
        currentTask = taskScheduler.schedule(
                retentionService::runCleanup,
                new CronTrigger(cron)
        );
        log.info("Retention scheduled with cron: {}", cron);
    }

    public static String buildCron(String frequency, String timeOfDay) {
        String[] parts = timeOfDay.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        return switch (frequency) {
            case "6h"     -> String.format("0 %d */6 * * *", minute);
            case "12h"    -> String.format("0 %d */12 * * *", minute);
            case "weekly" -> String.format("0 %d %d * * 0", minute, hour);
            default       -> String.format("0 %d %d * * *", minute, hour);
        };
    }
}
