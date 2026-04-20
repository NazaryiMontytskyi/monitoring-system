package com.nmontytskyi.monitoring.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "monitoring")
public class MonitoringProperties {

    private String serverUrl = "http://localhost:8080";
    private String serviceName;
    private String serviceHost = "localhost";
    private int servicePort = 8080;
    private String actuatorUrl;
    private boolean enabled = true;
}
