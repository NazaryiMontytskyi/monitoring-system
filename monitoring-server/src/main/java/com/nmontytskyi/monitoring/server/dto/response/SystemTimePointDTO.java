package com.nmontytskyi.monitoring.server.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
