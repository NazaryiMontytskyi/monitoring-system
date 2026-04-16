package com.nmontytskyi.monitoring.server.dto.response;

import com.nmontytskyi.monitoring.model.HealthStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {

    private List<ServiceSummary> services;
    private int totalServices;
    private long healthyCount;
    private long degradedCount;
    private long downCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceSummary {
        private Long id;
        private String name;
        private HealthStatus status;
        private Double avgResponseTimeMs;
        private Double uptimePercent;
        private Double cpuUsage;
    }
}
