package com.nmontytskyi.monitoring.collector;

import com.nmontytskyi.monitoring.model.BusinessMetric;
import com.nmontytskyi.monitoring.model.MetricSnapshot;

import java.util.List;

/**
 * Contract for a component that sends collected metrics to the {@code monitoring-server}.
 *
 * <p>Implemented in {@code monitoring-spring-boot-starter} by
 * {@code HttpMetricsReporter}, which performs HTTP POST requests to the server's REST API.
 * Supports both single snapshot delivery and batch delivery
 * to reduce network overhead.
 *
 * <p>Implementations must be resilient to temporary server unavailability:
 * on delivery failure, metrics should be buffered and resent
 * once the connection is restored.
 */
public interface MetricsReporter {

    /**
     * Sends a single technical metric snapshot to the monitoring server.
     *
     * @param snapshot snapshot of a service state or a specific endpoint state
     */
    void report(MetricSnapshot snapshot);

    /**
     * Sends a batch of technical snapshots in a single HTTP request.
     * Used to reduce network overhead during buffered delivery.
     *
     * @param snapshots list of snapshots to send
     */
    void reportBatch(List<MetricSnapshot> snapshots);

    /**
     * Sends a single business metric to the monitoring server.
     * Invoked by the AOP aspect after each successful execution of a method
     * annotated with {@link com.nmontytskyi.monitoring.annotation.TrackBusinessMetric}.
     *
     * @param metric business metric to send
     */
    void reportBusiness(BusinessMetric metric);
}
