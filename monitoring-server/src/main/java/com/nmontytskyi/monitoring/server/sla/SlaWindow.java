package com.nmontytskyi.monitoring.server.sla;

import java.time.Duration;

/**
 * Defines the time window over which an SLA report is calculated.
 *
 * <p>Each constant encapsulates the {@link Duration} that is subtracted
 * from the current timestamp to obtain the start of the reporting window.
 * Used as a request parameter in {@code GET /api/services/{id}/sla?window=}.
 */
public enum SlaWindow {

    /** Last 1 hour. */
    HOUR(Duration.ofHours(1)),

    /** Last 24 hours. */
    DAY(Duration.ofDays(1)),

    /** Last 7 days. */
    WEEK(Duration.ofDays(7)),

    /** Last 30 days. */
    MONTH(Duration.ofDays(30));

    private final Duration duration;

    SlaWindow(Duration duration) {
        this.duration = duration;
    }

    /**
     * Returns the {@link Duration} associated with this window.
     *
     * @return the duration from "now" back to the start of the window
     */
    public Duration getDuration() {
        return duration;
    }
}
