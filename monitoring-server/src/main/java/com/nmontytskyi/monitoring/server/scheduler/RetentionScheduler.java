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
