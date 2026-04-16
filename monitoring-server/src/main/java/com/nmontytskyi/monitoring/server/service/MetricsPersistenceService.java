package com.nmontytskyi.monitoring.server.service;

import com.nmontytskyi.monitoring.detector.AnomalyDetector;
import com.nmontytskyi.monitoring.server.dto.request.MetricSnapshotRequest;
import com.nmontytskyi.monitoring.server.dto.response.AggregateMetricsResponse;
import com.nmontytskyi.monitoring.server.dto.response.MetricRecordResponse;
import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity;
import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity.MetricSource;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import com.nmontytskyi.monitoring.server.exception.ServiceNotFoundException;
import com.nmontytskyi.monitoring.server.repository.MetricRecordRepository;
import com.nmontytskyi.monitoring.server.repository.RegisteredServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsPersistenceService {

    private final MetricRecordRepository metricRecordRepository;
    private final RegisteredServiceRepository serviceRepository;
    private final AnomalyDetector anomalyDetector;

    @Transactional
    public MetricRecordResponse saveEndpointSnapshot(MetricSnapshotRequest request) {
        RegisteredServiceEntity service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ServiceNotFoundException(request.getServiceId()));

        MetricRecordEntity entity = MetricRecordEntity.builder()
                .service(service)
                .endpoint(request.getEndpoint())
                .responseTimeMs(request.getResponseTimeMs())
                .status(request.getStatus())
                .cpuUsage(request.getCpuUsage())
                .heapUsedMb(request.getHeapUsedMb())
                .heapMaxMb(request.getHeapMaxMb())
                .errorMessage(request.getErrorMessage())
                .source(MetricSource.PUSH)
                .recordedAt(LocalDateTime.now())
                .build();

        MetricRecordEntity saved = metricRecordRepository.save(entity);

        List<MetricRecordEntity> history =
                metricRecordRepository.findTop100ByServiceIdOrderByRecordedAtDesc(request.getServiceId());

        List<Double> historicalValues = history.stream()
                .map(m -> (double) m.getResponseTimeMs())
                .toList();

        AnomalyDetector.AnomalyResult result =
                anomalyDetector.analyze((double) saved.getResponseTimeMs(), historicalValues);

        if (result.hasSufficientData()) {
            saved.setZScore(result.getZScore());
            saved.setAnomaly(result.isAnomaly());
            saved = metricRecordRepository.save(saved);
            log.debug("Anomaly detection for service {}: zScore={}, anomaly={}",
                    request.getServiceId(), result.getZScore(), result.isAnomaly());
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Optional<MetricRecordResponse> getLatest(Long serviceId) {
        if (!serviceRepository.existsById(serviceId)) {
            throw new ServiceNotFoundException(serviceId);
        }
        return metricRecordRepository.findTopByServiceIdOrderByRecordedAtDesc(serviceId)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AggregateMetricsResponse getAggregate(Long serviceId, LocalDateTime from, LocalDateTime to) {
        if (!serviceRepository.existsById(serviceId)) {
            throw new ServiceNotFoundException(serviceId);
        }
        List<Object[]> results = metricRecordRepository.aggregateByServiceAndPeriod(serviceId, from, to);
        if (results.isEmpty() || results.get(0)[0] == null) {
            return AggregateMetricsResponse.builder()
                    .avgResponseTimeMs(0)
                    .minResponseTimeMs(0)
                    .maxResponseTimeMs(0)
                    .totalRequests(0)
                    .uptimePercent(0)
                    .build();
        }
        Object[] row = results.get(0);
        double avg = ((Number) row[0]).doubleValue();
        double min = ((Number) row[1]).doubleValue();
        double max = ((Number) row[2]).doubleValue();
        long total = ((Number) row[3]).longValue();
        long upCount = ((Number) row[4]).longValue();
        double uptimePercent = total > 0 ? (upCount * 100.0 / total) : 0.0;

        return AggregateMetricsResponse.builder()
                .avgResponseTimeMs(avg)
                .minResponseTimeMs(min)
                .maxResponseTimeMs(max)
                .totalRequests(total)
                .uptimePercent(uptimePercent)
                .build();
    }

    private MetricRecordResponse toResponse(MetricRecordEntity e) {
        return MetricRecordResponse.builder()
                .id(e.getId())
                .serviceId(e.getService().getId())
                .endpoint(e.getEndpoint())
                .responseTimeMs(e.getResponseTimeMs())
                .status(e.getStatus())
                .cpuUsage(e.getCpuUsage())
                .heapUsedMb(e.getHeapUsedMb())
                .heapMaxMb(e.getHeapMaxMb())
                .errorMessage(e.getErrorMessage())
                .anomaly(e.isAnomaly())
                .zScore(e.getZScore())
                .source(e.getSource().name())
                .recordedAt(e.getRecordedAt())
                .build();
    }
}
