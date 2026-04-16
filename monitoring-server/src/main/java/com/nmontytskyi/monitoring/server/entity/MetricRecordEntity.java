package com.nmontytskyi.monitoring.server.entity;

import com.nmontytskyi.monitoring.model.HealthStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * JPA entity representing a single point-in-time measurement for a microservice.
 *
 * <p>Records arrive via two channels:
 * <ul>
 *   <li><b>PUSH</b> — sent by the {@code monitoring-spring-boot-starter} AOP aspect
 *       after each intercepted HTTP request. Contains endpoint-level granularity
 *       (response time, status) but no system-level metrics (CPU, heap).</li>
 *   <li><b>PULL</b> — collected by the server-side scheduler via Actuator endpoints
 *       every 30 seconds. Contains service-level metrics (CPU, heap) but no
 *       endpoint granularity.</li>
 * </ul>
 *
 * <p>The {@code anomaly} and {@code zScore} fields are populated by
 * {@code AnomalyDetector} immediately after the record is persisted.
 *
 * <p>Maps to the {@code metric_records} table.
 * A composite index on {@code (service_id, recorded_at DESC)} supports
 * efficient time-range and anomaly-window queries.
 */
@Entity
@Table(
    name = "metric_records",
    indexes = @Index(
        name = "idx_metric_records_service_time",
        columnList = "service_id, recorded_at DESC"
    )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricRecordEntity {

    /** Surrogate primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The service this record belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private RegisteredServiceEntity service;

    /**
     * Endpoint in {@code "HTTP_METHOD /path"} format.
     * Populated only for PUSH records. {@code null} for PULL records.
     */
    @Column(length = 255)
    private String endpoint;

    /** Response time of the request in milliseconds. */
    @Column(name = "response_time_ms", nullable = false)
    @Builder.Default
    private long responseTimeMs = 0L;

    /** Health status at the time of the measurement. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private HealthStatus status = HealthStatus.UNKNOWN;

    /**
     * CPU usage in the range [0.0, 1.0].
     * {@code null} for PUSH records (starter does not collect CPU data).
     */
    @Column(name = "cpu_usage")
    private Double cpuUsage;

    /**
     * Used JVM heap memory in megabytes.
     * {@code null} for PUSH records.
     */
    @Column(name = "heap_used_mb")
    private Long heapUsedMb;

    /**
     * Maximum available JVM heap memory in megabytes.
     * {@code null} for PUSH records.
     */
    @Column(name = "heap_max_mb")
    private Long heapMaxMb;

    /**
     * Error message if the service returned an exception or HTTP 5xx.
     * {@code null} on a successful response.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * {@code true} if the Z-score of {@code responseTimeMs} exceeds the
     * configured anomaly threshold (default: 3σ).
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean anomaly = false;

    /**
     * Z-score of {@code responseTimeMs} relative to the last 100 records.
     * {@code 0.0} when there is insufficient history.
     */
    @Column(name = "z_score", nullable = false)
    @Builder.Default
    private double zScore = 0.0;

    /**
     * Origin of the record: {@code PULL} (server-initiated) or {@code PUSH} (starter-initiated).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    @Builder.Default
    private MetricSource source = MetricSource.PULL;

    /** Timestamp when this measurement was recorded. */
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    /**
     * Origin of a {@link MetricRecordEntity}: whether the server pulled it
     * from the Actuator endpoint or the starter pushed it after an HTTP request.
     */
    public enum MetricSource {
        /** Collected by the server-side scheduler via {@code /actuator} endpoints. */
        PULL,
        /** Sent by the {@code monitoring-spring-boot-starter} AOP aspect. */
        PUSH
    }
}
