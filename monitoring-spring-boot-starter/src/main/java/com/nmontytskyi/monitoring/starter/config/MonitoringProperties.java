package com.nmontytskyi.monitoring.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the monitoring starter.
 *
 * <p>All properties can be set via {@code application.yml} under the {@code monitoring} prefix.
 * When the service is annotated with {@code @MonitoredMicroservice}, annotation attribute values
 * are used as defaults and can be overridden by YAML.
 */
@Data
@ConfigurationProperties(prefix = "monitoring")
public class MonitoringProperties {

    /** Base URL of the central monitoring server. */
    private String serverUrl = "http://localhost:8080";

    /** Logical service name registered with the monitoring server. */
    private String serviceName;

    /** Host of this service, reported during registration. */
    private String serviceHost = "localhost";

    /** Port of this service, reported during registration. */
    private int servicePort = 8080;

    /** Actuator base URL (auto-derived from serviceHost:servicePort if blank). */
    private String actuatorUrl;

    /** Set to {@code false} to completely disable monitoring for this service. */
    private boolean enabled = true;

    /**
     * When {@code true}, all {@code @RestController} methods are intercepted automatically.
     * Individual methods still honour {@code @MonitoredEndpoint} for name overrides.
     */
    private boolean trackAllEndpoints = false;

    /** Interval between scheduled metric flushes, in milliseconds. */
    private long bufferFlushIntervalMs = 5000;

    /** Maximum queue size before an immediate flush is triggered. */
    private int bufferMaxSize = 100;

    /** SLA: maximum acceptable average response time in milliseconds. */
    private long slaResponseTimeMs = 1000;

    /** SLA: minimum required uptime as a percentage. */
    private double slaUptimePercent = 99.9;

    /** SLA: maximum acceptable error rate as a percentage. */
    private double slaErrorRatePercent = 5.0;
}
