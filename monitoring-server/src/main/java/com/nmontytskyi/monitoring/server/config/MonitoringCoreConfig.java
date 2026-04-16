package com.nmontytskyi.monitoring.server.config;

import com.nmontytskyi.monitoring.detector.AnomalyDetector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MonitoringCoreConfig {

    @Bean
    public AnomalyDetector anomalyDetector() {
        return new AnomalyDetector();
    }
}
