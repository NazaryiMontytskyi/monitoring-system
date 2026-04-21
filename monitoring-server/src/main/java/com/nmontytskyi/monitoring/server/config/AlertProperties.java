package com.nmontytskyi.monitoring.server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "monitoring.alert")
public class AlertProperties {

    private boolean enabled = true;
    private int evaluationWindowMinutes = 60;
    private String notificationFrom = "monitoring@example.com";
    private String notificationTo = "admin@example.com";
}
