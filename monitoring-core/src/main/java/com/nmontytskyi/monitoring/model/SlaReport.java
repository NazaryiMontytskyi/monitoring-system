package com.nmontytskyi.monitoring.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SLA compliance report for a microservice over a defined time period.
 *
 * <p>Computed by {@code SlaEvaluationService} based on stored
 * {@link MetricSnapshot} records within the specified time range.
 * Contains both actual metric values and percentiles,
 * as well as the results of comparison against SLA thresholds.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaReport {

    /**
     * Identifier of the service for which the report was generated.
     */
    private String serviceId;

    /**
     * Start of the reporting time window.
     */
    private LocalDateTime from;

    /**
     * End of the reporting time window.
     */
    private LocalDateTime to;

    // ── Actual values ────────────────────────────────────────────────────────

    /**
     * Actual uptime percentage of the service during the specified period.
     */
    private double actualUptimePercent;

    /**
     * Actual average response time in milliseconds during the specified period.
     */
    private double actualAvgResponseTimeMs;

    /**
     * Actual error rate as a percentage of total requests.
     */
    private double actualErrorRatePercent;

    // ── Response time percentiles ────────────────────────────────────────────

    /**
     * Median response time (P50) — 50% of requests completed faster than this value.
     */
    private long p50ResponseTimeMs;

    /**
     * 95th percentile response time — 95% of requests completed faster than this value.
     */
    private long p95ResponseTimeMs;

    /**
     * 99th percentile response time — 99% of requests completed faster than this value.
     * Represents the "tail latency" — the actual experience of the slowest requests.
     */
    private long p99ResponseTimeMs;

    // ── SLA parameters and compliance ────────────────────────────────────────

    /**
     * SLA parameters against which compliance was evaluated.
     */
    private SlaDefinition sla;

    /**
     * {@code true} if the actual uptime satisfies the SLA requirement.
     */
    private boolean uptimeMet;

    /**
     * {@code true} if the actual average response time satisfies the SLA requirement.
     */
    private boolean responseTimeMet;

    /**
     * {@code true} if the actual error rate satisfies the SLA requirement.
     */
    private boolean errorRateMet;

    /**
     * @return {@code true} if at least one SLA requirement is violated
     */
    public boolean isSlaBreached() {
        return !uptimeMet || !responseTimeMet || !errorRateMet;
    }

    /**
     * Calculates the overall SLA compliance percentage
     * as the fraction of satisfied requirements out of the total.
     *
     * @return percentage from 0.0 to 100.0
     */
    public double getCompliancePercent() {
        int met = (uptimeMet ? 1 : 0) + (responseTimeMet ? 1 : 0) + (errorRateMet ? 1 : 0);
        return (met / 3.0) * 100.0;
    }
}
