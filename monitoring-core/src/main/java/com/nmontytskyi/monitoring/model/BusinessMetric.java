package com.nmontytskyi.monitoring.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A business metric recorded for a microservice at a specific point in time.
 *
 * <p>Collected via the {@link com.nmontytskyi.monitoring.annotation.TrackBusinessMetric}
 * annotation and sent to the {@code monitoring-server} by the starter.
 * Allows correlating business activity with technical metrics:
 * for example, seeing how a CPU spike affects the number of processed orders.
 *
 * <p>Example: {@code name="orders.created"}, {@code value=1.0}, {@code unit="count"}
 * is recorded on each successful invocation of a method annotated with
 * {@code @TrackBusinessMetric}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessMetric {

    /**
     * Identifier of the service this metric belongs to.
     */
    private String serviceId;

    /**
     * Metric name in {@code "entity.action"} format.
     * Examples: {@code "orders.created"}, {@code "payments.failed"}.
     */
    private String name;

    /**
     * Metric value. For counters — {@code 1.0} per invocation
     * (aggregation is performed server-side).
     */
    private double value;

    /**
     * Unit of measurement: {@code "count"}, {@code "uah"}, {@code "items"}, etc.
     */
    private String unit;

    /**
     * Human-readable description shown in the web interface.
     */
    private String description;

    /**
     * Timestamp when the metric was recorded.
     */
    private LocalDateTime recordedAt;
}
