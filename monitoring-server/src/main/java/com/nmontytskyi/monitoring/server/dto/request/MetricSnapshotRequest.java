package com.nmontytskyi.monitoring.server.dto.request;

import com.nmontytskyi.monitoring.model.HealthStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound DTO carrying a single metric observation pushed by a monitored microservice
 * or produced internally by the Actuator polling scheduler.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricSnapshotRequest {

    @NotNull
    private Long serviceId;

    @NotBlank
    private String endpoint;

    private long responseTimeMs;

    @NotNull
    private HealthStatus status;

    private Double cpuUsage;

    private Long heapUsedMb;

    private Long heapMaxMb;

    private String errorMessage;

    private Long nonHeapUsedMb;

    private Integer threadsLive;

    private Integer threadsDaemon;

    private Double gcPauseMs;

    private Double processCpuUsage;
}
