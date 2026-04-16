package com.nmontytskyi.monitoring.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A single snapshot of a microservice state at a specific point in time.
 *
 * <p>Can represent either the overall service state (when {@code endpoint} is {@code null})
 * or the state of a specific endpoint (when {@code endpoint} is set).
 *
 * <p>The {@code anomaly} and {@code zScore} fields are populated by
 * {@link com.nmontytskyi.monitoring.detector.AnomalyDetector} based on
 * statistical analysis of historical values.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricSnapshot {

    /**
     * Identifier of the service this snapshot belongs to.
     * Corresponds to {@link ServiceInfo#getId()}.
     */
    private String serviceId;

    /**
     * Endpoint name in {@code "HTTP_METHOD /path"} format.
     * Example: {@code "GET /orders"}, {@code "POST /payments"}.
     * {@code null} if the snapshot represents the overall service state
     * rather than a specific endpoint.
     */
    private String endpoint;

    /**
     * Response time of the request in milliseconds.
     */
    private long responseTimeMs;

    /**
     * Health status of the service or endpoint at the time of the snapshot.
     */
    private HealthStatus status;

    /**
     * CPU usage percentage in the range from 0.0 to 1.0.
     * For example: {@code 0.12} means 12% CPU load.
     * Retrieved from {@code /actuator/metrics/system.cpu.usage}.
     */
    private double cpuUsage;

    /**
     * Amount of used JVM heap memory in megabytes.
     * Retrieved from {@code /actuator/metrics/jvm.memory.used}.
     */
    private long heapUsedMb;

    /**
     * Maximum available JVM heap memory in megabytes.
     * Retrieved from {@code /actuator/metrics/jvm.memory.max}.
     */
    private long heapMaxMb;

    /**
     * Error message if the service returned an exception or HTTP 5xx.
     * {@code null} on successful response.
     */
    private String errorMessage;

    /**
     * Timestamp when the snapshot was recorded.
     */
    private LocalDateTime recordedAt;

    // ── Anomaly detection ────────────────────────────────────────────────────

    /**
     * {@code true} if the {@code responseTimeMs} value statistically deviates
     * from this service's own norm (|Z-score| exceeds the AnomalyDetector threshold).
     */
    @Builder.Default
    private boolean anomaly = false;

    /**
     * Z-score of the current {@code responseTimeMs} relative to
     * the mean and standard deviation of the last N measurements.
     * {@code 0.0} if there is insufficient data for calculation.
     */
    @Builder.Default
    private double zScore = 0.0;
}
