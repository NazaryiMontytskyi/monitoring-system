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
}
