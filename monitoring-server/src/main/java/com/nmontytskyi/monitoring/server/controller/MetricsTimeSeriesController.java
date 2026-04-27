package com.nmontytskyi.monitoring.server.controller;

import com.nmontytskyi.monitoring.server.dto.response.MetricTimePointDTO;
import com.nmontytskyi.monitoring.server.dto.response.SystemTimePointDTO;
import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity;
import com.nmontytskyi.monitoring.server.repository.MetricTimeSeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsTimeSeriesController {

    private final MetricTimeSeriesRepository timeSeriesRepository;

    @GetMapping("/{serviceId}/history")
    public List<MetricTimePointDTO> getServiceHistory(
            @PathVariable Long serviceId,
            @RequestParam(defaultValue = "30") int minutes,
            @RequestParam(defaultValue = "60") int limit) {

        LocalDateTime from = LocalDateTime.now().minusMinutes(minutes);
        return timeSeriesRepository
                .findRecentByService(serviceId, from, MetricRecordEntity.MetricSource.PULL, PageRequest.of(0, limit))
                .stream()
                .map(this::toTimePoint)
                .toList();
    }

    @GetMapping("/system/history")
    public List<SystemTimePointDTO> getSystemHistory(
            @RequestParam(defaultValue = "30") int minutes,
            @RequestParam(defaultValue = "60") int limit) {

        LocalDateTime from = LocalDateTime.now().minusMinutes(minutes);
        return timeSeriesRepository.findSystemAggregated(from, limit)
                .stream()
                .map(this::toSystemPoint)
                .toList();
    }

    private MetricTimePointDTO toTimePoint(MetricRecordEntity e) {
        return MetricTimePointDTO.builder()
                .recordedAt(e.getRecordedAt())
                .responseTimeMs(e.getResponseTimeMs())
                .status(e.getStatus())
                .cpuUsage(e.getCpuUsage())
                .heapUsedMb(e.getHeapUsedMb())
                .heapMaxMb(e.getHeapMaxMb())
                .nonHeapUsedMb(e.getNonHeapUsedMb())
                .threadsLive(e.getThreadsLive())
                .threadsDaemon(e.getThreadsDaemon())
                .gcPauseMs(e.getGcPauseMs())
                .processCpuUsage(e.getProcessCpuUsage())
                .anomaly(e.isAnomaly())
                .zScore(e.getZScore())
                .build();
    }

    private SystemTimePointDTO toSystemPoint(Object[] row) {
        return SystemTimePointDTO.builder()
                .recordedAt(((Timestamp) row[0]).toLocalDateTime())
                .avgResponseTimeMs(toDouble(row[1]))
                .maxResponseTimeMs(toDouble(row[2]))
                .avgCpuUsage(toDouble(row[3]))
                .avgHeapUsedMb(toDouble(row[4]))
                .servicesUp(toLong(row[5]))
                .servicesDown(toLong(row[6]))
                .servicesDegraded(toLong(row[7]))
                .anomalyCount(toLong(row[8]))
                .build();
    }

    private double toDouble(Object o) {
        return o != null ? ((Number) o).doubleValue() : 0.0;
    }

    private long toLong(Object o) {
        return o != null ? ((Number) o).longValue() : 0L;
    }
}
