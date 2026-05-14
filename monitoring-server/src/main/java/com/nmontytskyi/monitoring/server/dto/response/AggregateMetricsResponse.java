package com.nmontytskyi.monitoring.server.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO carrying aggregated metric statistics (average, min, max, percentiles) for
 * a service over a specified time window.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregateMetricsResponse {

    private double avgResponseTimeMs;
    private double minResponseTimeMs;
    private double maxResponseTimeMs;
    private long totalRequests;
    private double uptimePercent;
}
