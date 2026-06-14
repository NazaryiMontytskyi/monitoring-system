package com.nmontytskyi.demo.analytics;

import com.nmontytskyi.monitoring.starter.annotation.MonitoredMicroservice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MonitoredMicroservice(
        name = "analytics-service",
        trackAllEndpoints = true,
        bufferFlushIntervalMs = 5000
)
public class AnalyticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}
