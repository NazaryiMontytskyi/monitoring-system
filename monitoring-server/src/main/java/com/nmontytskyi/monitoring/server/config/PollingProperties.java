package com.nmontytskyi.monitoring.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "monitoring.polling")
public class PollingProperties {

    private boolean enabled = true;
    private int intervalSeconds = 30;
    private int timeoutSeconds = 5;
    private int connectTimeoutSeconds = 3;
}
