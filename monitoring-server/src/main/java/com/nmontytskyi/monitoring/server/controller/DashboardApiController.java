package com.nmontytskyi.monitoring.server.controller;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.dto.response.AggregateMetricsResponse;
import com.nmontytskyi.monitoring.server.dto.response.DashboardSummaryResponse;
import com.nmontytskyi.monitoring.server.dto.response.MetricRecordResponse;
import com.nmontytskyi.monitoring.server.dto.response.ServiceResponse;
import com.nmontytskyi.monitoring.server.service.MetricsPersistenceService;
import com.nmontytskyi.monitoring.server.service.RegisteredServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Validated
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Aggregated monitoring dashboard")
public class DashboardApiController {

    private final RegisteredServiceService registeredServiceService;
    private final MetricsPersistenceService metricsPersistenceService;

    @GetMapping
    @Operation(summary = "Get aggregated state of all monitored services")
    @ApiResponse(responseCode = "200", description = "Dashboard summary returned")
    public ResponseEntity<DashboardSummaryResponse> getDashboard() {
        List<ServiceResponse> services = registeredServiceService.findAll();
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusHours(24);

        List<DashboardSummaryResponse.ServiceSummary> summaries = services.stream()
                .map(svc -> buildSummary(svc, from, to))
                .toList();

        Map<HealthStatus, Long> counts = services.stream()
                .collect(Collectors.groupingBy(ServiceResponse::getStatus, Collectors.counting()));

        return ResponseEntity.ok(DashboardSummaryResponse.builder()
                .services(summaries)
                .totalServices(services.size())
                .healthyCount(counts.getOrDefault(HealthStatus.UP, 0L))
                .degradedCount(counts.getOrDefault(HealthStatus.DEGRADED, 0L))
                .downCount(counts.getOrDefault(HealthStatus.DOWN, 0L))
                .build());
    }

    private DashboardSummaryResponse.ServiceSummary buildSummary(
            ServiceResponse svc, LocalDateTime from, LocalDateTime to) {
        Double avgMs = null;
        Double uptimePct = null;
        Double cpuUsage = null;

        try {
            AggregateMetricsResponse agg =
                    metricsPersistenceService.getAggregate(svc.getId(), from, to);
            if (agg.getTotalRequests() > 0) {
                avgMs = agg.getAvgResponseTimeMs();
                uptimePct = agg.getUptimePercent();
            }
        } catch (Exception e) {
            // no metrics for this service yet
        }

        try {
            cpuUsage = metricsPersistenceService.getLatest(svc.getId())
                    .map(MetricRecordResponse::getCpuUsage)
                    .orElse(null);
        } catch (Exception e) {
            // no metrics for this service yet
        }

        return DashboardSummaryResponse.ServiceSummary.builder()
                .id(svc.getId())
                .name(svc.getName())
                .status(svc.getStatus())
                .avgResponseTimeMs(avgMs)
                .uptimePercent(uptimePct)
                .cpuUsage(cpuUsage)
                .build();
    }
}
