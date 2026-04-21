package com.nmontytskyi.monitoring.server.service;

import com.nmontytskyi.monitoring.detector.AnomalyDetector;
import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.alert.AlertEvaluationService;
import com.nmontytskyi.monitoring.server.dto.request.MetricSnapshotRequest;
import com.nmontytskyi.monitoring.server.dto.response.AggregateMetricsResponse;
import com.nmontytskyi.monitoring.server.dto.response.MetricRecordResponse;
import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity;
import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity.MetricSource;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import com.nmontytskyi.monitoring.server.repository.MetricRecordRepository;
import com.nmontytskyi.monitoring.server.repository.RegisteredServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsPersistenceServiceTest {

    @Mock
    private MetricRecordRepository metricRecordRepository;

    @Mock
    private RegisteredServiceRepository serviceRepository;

    @Mock
    private AnomalyDetector anomalyDetector;

    @Mock
    private AlertEvaluationService alertEvaluationService;

    @InjectMocks
    private MetricsPersistenceService service;

    @Test
    void saveEndpointSnapshot_success_returnsResponse() {
        RegisteredServiceEntity svc = buildService(1L);
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(svc));
        when(metricRecordRepository.save(any())).thenAnswer(inv -> {
            MetricRecordEntity e = inv.getArgument(0);
            e.setId(10L);
            return e;
        });
        when(metricRecordRepository.findTop100ByServiceIdOrderByRecordedAtDesc(1L))
                .thenReturn(Collections.emptyList());
        when(anomalyDetector.analyze(anyDouble(), anyList()))
                .thenReturn(AnomalyDetector.AnomalyResult.insufficient());

        MetricSnapshotRequest req = MetricSnapshotRequest.builder()
                .serviceId(1L)
                .endpoint("GET /items")
                .responseTimeMs(200L)
                .status(HealthStatus.UP)
                .build();

        MetricRecordResponse result = service.saveEndpointSnapshot(req);

        assertThat(result.getServiceId()).isEqualTo(1L);
        assertThat(result.getEndpoint()).isEqualTo("GET /items");
        assertThat(result.getSource()).isEqualTo("PUSH");
    }

    @Test
    void saveEndpointSnapshot_anomalyDetectedAndPersisted() {
        RegisteredServiceEntity svc = buildService(1L);
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(svc));

        MetricRecordEntity saved = MetricRecordEntity.builder()
                .id(10L)
                .service(svc)
                .endpoint("GET /items")
                .responseTimeMs(5000L)
                .status(HealthStatus.UP)
                .source(MetricSource.PUSH)
                .recordedAt(LocalDateTime.now())
                .build();

        when(metricRecordRepository.save(any())).thenReturn(saved);
        when(metricRecordRepository.findTop100ByServiceIdOrderByRecordedAtDesc(1L))
                .thenReturn(buildHistory(svc, 50, 100L));
        when(anomalyDetector.analyze(anyDouble(), anyList()))
                .thenReturn(AnomalyDetector.AnomalyResult.of(4.2, true));

        MetricSnapshotRequest req = MetricSnapshotRequest.builder()
                .serviceId(1L)
                .endpoint("GET /items")
                .responseTimeMs(5000L)
                .status(HealthStatus.UP)
                .build();

        service.saveEndpointSnapshot(req);

        // initial save + save after anomaly detection
        verify(metricRecordRepository, times(2)).save(any());
        assertThat(saved.isAnomaly()).isTrue();
        assertThat(saved.getZScore()).isEqualTo(4.2);
    }

    @Test
    void getAggregate_calculatesUptimePercent() {
        when(serviceRepository.existsById(1L)).thenReturn(true);
        // [avgMs, minMs, maxMs, total, upCount]
        Object[] row = {250.0, 100L, 500L, 10L, 8L};
        List<Object[]> rows = java.util.Collections.singletonList(row);
        when(metricRecordRepository.aggregateByServiceAndPeriod(eq(1L), any(), any()))
                .thenReturn(rows);

        AggregateMetricsResponse result = service.getAggregate(
                1L, LocalDateTime.now().minusHours(1), LocalDateTime.now());

        assertThat(result.getAvgResponseTimeMs()).isEqualTo(250.0);
        assertThat(result.getTotalRequests()).isEqualTo(10L);
        assertThat(result.getUptimePercent()).isEqualTo(80.0);
    }

    private RegisteredServiceEntity buildService(Long id) {
        return RegisteredServiceEntity.builder()
                .id(id)
                .name("test-service")
                .host("localhost")
                .port(8080)
                .status(HealthStatus.UNKNOWN)
                .build();
    }

    private List<MetricRecordEntity> buildHistory(RegisteredServiceEntity svc, int count, long baseMs) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> MetricRecordEntity.builder()
                        .id((long) i)
                        .service(svc)
                        .responseTimeMs(baseMs + i)
                        .status(HealthStatus.UP)
                        .source(MetricSource.PUSH)
                        .recordedAt(LocalDateTime.now().minusMinutes(i))
                        .build())
                .toList();
    }
}
