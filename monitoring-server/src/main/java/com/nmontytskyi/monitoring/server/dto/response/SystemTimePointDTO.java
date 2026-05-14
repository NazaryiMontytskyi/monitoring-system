package com.nmontytskyi.monitoring.server.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Single time-point DTO for system-wide aggregated charts on the dashboard.
 * Carries 30-second bucket averages for response time and CPU, plus counts of
 * services in each health state and the number of distinct anomalous services.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemTimePointDTO {

    private LocalDateTime recordedAt;
    private double avgResponseTimeMs;
    private double maxResponseTimeMs;
    private double avgCpuUsage;
    private double avgHeapUsedMb;
    private long servicesUp;
    private long servicesDown;
    private long servicesDegraded;
    private long anomalyCount;
}
