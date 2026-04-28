package com.nmontytskyi.monitoring.server.dto;

import com.nmontytskyi.monitoring.model.HealthStatus;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceWithSlaDto {
    private Long id;
    private String name;
    private HealthStatus status;
    private double uptimePercent;
    private long maxResponseTimeMs;
    private double maxErrorRatePercent;
    private String description;
}
