package com.nmontytskyi.monitoring.server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * JPA entity representing a single occurrence of a fired alert.
 *
 * <p>Created by {@code AlertEvaluationService} whenever a metric crosses an
 * {@link AlertRuleEntity}'s threshold and the rule's cooldown period has elapsed.
 * Each event is persisted and visible in the event log UI (FR-4).
 *
 * <p>Maps to the {@code alert_events} table.
 * A composite index on {@code (service_id, fired_at DESC)} supports
 * efficient queries in the paginated event log.
 */
@Entity
@Table(
    name = "alert_events",
    indexes = @Index(
        name = "idx_alert_events_service_time",
        columnList = "service_id, fired_at DESC"
    )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEventEntity {

    /** Surrogate primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The alert rule that triggered this event.
     * Retains a reference even if the rule is later disabled.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private AlertRuleEntity rule;

    /**
     * The service that violated the rule.
     * Denormalised here for efficient event log queries without joins.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private RegisteredServiceEntity service;

    /** Timestamp when the alert was evaluated and found to be in violation. */
    @Column(name = "fired_at", nullable = false)
    private LocalDateTime firedAt;

    /** The actual metric value that triggered the alert. */
    @Column(name = "metric_value", nullable = false)
    private double metricValue;

    /**
     * Human-readable description of the alert.
     * Example: {@code "order-service — avg response time 1250ms exceeded threshold 1000ms"}.
     */
    @Column(columnDefinition = "TEXT")
    private String message;

    /**
     * {@code true} if an email notification was successfully dispatched for this event.
     * {@code false} if email sending failed or is still pending.
     */
    @Column(name = "notification_sent", nullable = false)
    @Builder.Default
    private boolean notificationSent = false;
}
