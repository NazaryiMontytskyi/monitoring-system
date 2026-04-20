package com.nmontytskyi.monitoring.starter.client.dto;

import com.nmontytskyi.monitoring.model.HealthStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MetricPushRequest {

    private Long serviceId;
    private String endpoint;
    private long responseTimeMs;
    private HealthStatus status;
    private String errorMessage;
}
