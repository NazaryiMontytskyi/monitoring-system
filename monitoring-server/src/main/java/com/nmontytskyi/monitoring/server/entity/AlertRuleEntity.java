package com.nmontytskyi.monitoring.server.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * JPA entity representing a configurable alerting rule for a microservice.
 *
 * <p>An alert rule defines when the monitoring server should fire an alert:
 * when a specific metric ({@link MetricType}) crosses a threshold value
 * in the direction specified by {@link Comparator}.
 *
 * <p>Example: {@code metric_type = RESPONSE_TIME_AVG, comparator = GT, threshold = 1000}
 * means "fire an alert when the average response time exceeds 1000 ms".
 *
 * <p>Maps to the {@code alert_rules} table.
 */
@Entity
@Table(name = "alert_rules")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleEntity {

    /** Surrogate primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The service this rule applies to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private RegisteredServiceEntity service;

    /**
     * The metric this rule monitors.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 32)
    private MetricType metricType;

    /**
     * Comparison direction: {@code GT} (greater than) or {@code LT} (less than).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Comparator comparator;

    /** Numeric threshold value for the comparison. */
    @Column(nullable = false)
    private double threshold;

    /**
     * Whether this rule is active. Disabled rules are stored but not evaluated.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * Minimum minutes that must pass since the last alert for this rule before
     * another notification is sent. Prevents alert storms.
     */
    @Column(name = "cooldown_minutes", nullable = false)
    @Builder.Default
    private int cooldownMinutes = 15;

    /** Timestamp when this rule was created. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Metric types that can be monitored by an alert rule.
     */
    public enum MetricType {
        /** Average response time of the service in milliseconds. */
        RESPONSE_TIME_AVG,
        /** Service health status equals DOWN. */
        STATUS_DOWN,
        /** CPU usage percentage (0–100). */
        CPU_USAGE,
        /** Uptime percentage over the evaluation window. */
        UPTIME_PERCENT,
        /** Error rate as a percentage of total requests. */
        ERROR_RATE
    }

    /**
     * Comparison operator for evaluating metric values against the threshold.
     */
    public enum Comparator {
        /** Alert fires when {@code metricValue > threshold}. */
        GT,
        /** Alert fires when {@code metricValue < threshold}. */
        LT
    }
}
