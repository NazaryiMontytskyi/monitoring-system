package com.nmontytskyi.monitoring.server.dto.response;

import com.nmontytskyi.monitoring.model.HealthStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricRecordResponse {

    private Long id;
    private Long serviceId;
    private String endpoint;
    private long responseTimeMs;
    private HealthStatus status;
    private Double cpuUsage;
    private Long heapUsedMb;
    private Long heapMaxMb;
    private String errorMessage;
    private boolean anomaly;
    private double zScore;
    private String source;
    private LocalDateTime recordedAt;
}
