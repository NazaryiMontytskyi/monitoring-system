package com.nmontytskyi.monitoring.model;

/**
 * Represents the availability state of a monitored microservice.
 *
 * <p>Determined by the monitoring system based on the service response
 * and comparison of collected metrics against the defined SLA thresholds.
 */
public enum HealthStatus {

    /**
     * The service is responding and all metrics are within normal range.
     */
    UP,

    /**
     * The service is responding but one or more metrics exceed SLA thresholds.
     * For example: response time is higher than {@code maxResponseTimeMs},
     * or the error rate exceeds {@code maxErrorRatePercent}.
     */
    DEGRADED,

    /**
     * The service is not responding to requests or returns a critical error (5xx).
     */
    DOWN,

    /**
     * The state is unknown — no data is available for evaluation.
     * Typical for newly registered services before the first metrics collection.
     */
    UNKNOWN
}
