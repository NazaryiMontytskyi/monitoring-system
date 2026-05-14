package com.nmontytskyi.monitoring.server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the alerting subsystem.
 *
 * <p>Properties are bound under the {@code monitoring.alert} prefix and configure whether
 * alerting is enabled, the default evaluation window, and the sender/recipient email
 * addresses used by {@link com.nmontytskyi.monitoring.server.alert.AlertNotificationService}.
 *
 * @author Nazar Montytskyi
 * @see com.nmontytskyi.monitoring.server.alert.AlertEvaluationService
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "monitoring.alert")
public class AlertProperties {

    private boolean enabled = true;
    private int evaluationWindowMinutes = 60;
    private String notificationFrom = "monitoring@example.com";
    private String notificationTo = "admin@example.com";
}
