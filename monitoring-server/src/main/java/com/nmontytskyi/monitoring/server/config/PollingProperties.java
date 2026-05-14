package com.nmontytskyi.monitoring.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the Actuator polling subsystem.
 *
 * <p>All properties are bound under the {@code monitoring.polling} prefix and control
 * whether polling is enabled, the polling interval, and HTTP timeout values used by
 * {@link com.nmontytskyi.monitoring.server.polling.ActuatorClient}.
 *
 * @author Nazar Montytskyi
 * @see com.nmontytskyi.monitoring.server.polling.MetricsPollingScheduler
 */
@Data
@ConfigurationProperties(prefix = "monitoring.polling")
public class PollingProperties {

    private boolean enabled = true;
    private int intervalSeconds = 30;
    private int timeoutSeconds = 5;
    private int connectTimeoutSeconds = 3;
}
