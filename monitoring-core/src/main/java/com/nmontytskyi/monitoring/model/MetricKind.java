package com.nmontytskyi.monitoring.model;

/**
 * Specifies how a value tracked by {@link com.nmontytskyi.monitoring.annotation.TrackMetric}
 * should be interpreted in the monitoring system.
 */
public enum MetricKind {
    /** Monotonically increasing invocation count. */
    COUNTER,
    /** Instantaneous reading (e.g. queue depth, memory usage). */
    GAUGE,
    /** Elapsed duration of a method call. */
    TIMER
}
