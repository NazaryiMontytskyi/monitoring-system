package com.nmontytskyi.monitoring.server.dto.response;

import com.nmontytskyi.monitoring.model.HealthStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Single time-point DTO for per-service metric charts.
 * Carries response time, health status, CPU, memory, thread, and GC metrics
 * together with the anomaly flag and z-score for a specific polling instant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricTimePointDTO {

    private LocalDateTime recordedAt;
    private long responseTimeMs;
    private HealthStatus status;
    private Double cpuUsage;
    private Long heapUsedMb;
    private Long heapMaxMb;
    private Long nonHeapUsedMb;
    private Integer threadsLive;
    private Integer threadsDaemon;
    private Double gcPauseMs;
    private Double processCpuUsage;
    private boolean anomaly;
    private double zScore;
}
