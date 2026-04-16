package com.nmontytskyi.monitoring.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Monitoring Server application.
 *
 * <p>The server is the central component of the monitoring system.
 * It collects metrics from registered microservices via two channels:
 * <ul>
 *   <li><b>Push</b> — microservices send {@code MetricSnapshot} data to the REST API
 *       via the embedded {@code monitoring-spring-boot-starter}.</li>
 *   <li><b>Pull</b> — a scheduler periodically calls {@code /actuator} endpoints
 *       on every registered service to collect health and resource metrics.</li>
 * </ul>
 *
 * <p>The application exposes a REST API (FR-2), a web dashboard (FR-3),
 * and an alerting engine (FR-4).
 */
@SpringBootApplication
@EnableScheduling
public class MonitoringServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonitoringServerApplication.class, args);
    }
}
