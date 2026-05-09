package com.nmontytskyi.monitoring.server.scheduler;

import com.nmontytskyi.monitoring.server.service.AppSettingsService;
import com.nmontytskyi.monitoring.server.service.RetentionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetentionSchedulerTest {

    @Mock private RetentionService retentionService;
    @Mock private AppSettingsService settingsService;
    @Mock private TaskScheduler taskScheduler;
    @Mock private ScheduledFuture<?> scheduledFuture;

    @InjectMocks private RetentionScheduler retentionScheduler;

    @SuppressWarnings("unchecked")
    @Test
    void reschedule_schedulesWithDefaultCron_whenNoSettingStored() {
        when(settingsService.get("retention.cron", "0 0 3 * * *")).thenReturn("0 0 3 * * *");
        when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
                .thenReturn((ScheduledFuture) scheduledFuture);

        retentionScheduler.reschedule();

        verify(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void reschedule_schedulesWithCustomCron() {
        when(settingsService.get("retention.cron", "0 0 3 * * *")).thenReturn("0 0 2 * * *");
        when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
                .thenReturn((ScheduledFuture) scheduledFuture);

        retentionScheduler.reschedule();

        verify(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));
        verify(settingsService).get("retention.cron", "0 0 3 * * *");
    }

    @SuppressWarnings("unchecked")
    @Test
    void reschedule_calledTwice_cancelsPreviousTaskBeforeSchedulingNew() {
        when(settingsService.get("retention.cron", "0 0 3 * * *")).thenReturn("0 0 3 * * *");
        when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
                .thenReturn((ScheduledFuture) scheduledFuture);

        retentionScheduler.reschedule();
        retentionScheduler.reschedule();

        verify(scheduledFuture).cancel(false);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(CronTrigger.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void scheduleFromSettings_invokesReschedule() {
        when(settingsService.get("retention.cron", "0 0 3 * * *")).thenReturn("0 0 3 * * *");
        when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
                .thenReturn((ScheduledFuture) scheduledFuture);

        retentionScheduler.scheduleFromSettings();

        verify(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));
    }
}
