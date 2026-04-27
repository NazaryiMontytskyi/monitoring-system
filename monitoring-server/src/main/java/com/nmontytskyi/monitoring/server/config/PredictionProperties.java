package com.nmontytskyi.monitoring.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "monitoring.prediction")
public class PredictionProperties {

    private boolean enabled = true;
    private double minRSquared = 0.3;
}
