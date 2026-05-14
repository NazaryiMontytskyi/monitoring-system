package com.nmontytskyi.monitoring.starter.client.dto;

import com.nmontytskyi.monitoring.model.HealthStatus;
import lombok.Builder;
import lombok.Data;

/**
 * DTO used by the monitoring starter to push a single metric snapshot to the server.
 *
 * <p>Serialised as JSON and sent via {@code POST /api/metrics/endpoint}.
 * For batch delivery see the batch endpoint used by
 * {@link com.nmontytskyi.monitoring.starter.buffer.MetricsBuffer}.
 */
@Data
@Builder
public class MetricPushRequest {

    private Long serviceId;
    private String endpoint;
    private long responseTimeMs;
    private HealthStatus status;
    private String errorMessage;
}
