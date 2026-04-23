package com.nmontytskyi.monitoring.server.polling;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.dto.request.MetricSnapshotRequest;
import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity.MetricSource;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import com.nmontytskyi.monitoring.server.repository.RegisteredServiceRepository;
import com.nmontytskyi.monitoring.server.service.MetricsPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "monitoring.polling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MetricsPollingScheduler {

    private final ActuatorClient actuatorClient;
    private final RegisteredServiceRepository serviceRepository;
    private final MetricsPersistenceService metricsPersistenceService;

    @Scheduled(fixedDelayString = "${monitoring.polling.interval-seconds:30}000")
    public void pollAllServices() {
        List<RegisteredServiceEntity> services = serviceRepository.findAll();
        log.info("Polling cycle started for {} services", services.size());
        services.forEach(this::pollSingleService);
    }

    private void pollSingleService(RegisteredServiceEntity service) {
        long startTime = System.currentTimeMillis();
        Optional<HealthStatus> healthOpt = actuatorClient.fetchHealth(service.getActuatorUrl());
        long responseTimeMs = System.currentTimeMillis() - startTime;

        if (healthOpt.isEmpty()) {
            return;
        }

        HealthStatus status = healthOpt.get();

        Optional<Double> heapUsed = actuatorClient.fetchMetricValue(service.getActuatorUrl(), "jvm.memory.used");
        Optional<Double> heapMax = actuatorClient.fetchMetricValue(service.getActuatorUrl(), "jvm.memory.max");
        Optional<Double> cpuUsage = actuatorClient.fetchMetricValue(service.getActuatorUrl(), "system.cpu.usage");
        Optional<Double> nonHeapUsed = actuatorClient.fetchMetricValue(service.getActuatorUrl(), "jvm.memory.used", "area:nonheap");
        Optional<Double> threadsLive = actuatorClient.fetchMetricValue(service.getActuatorUrl(), "jvm.threads.live");
        Optional<Double> threadsDaemon = actuatorClient.fetchMetricValue(service.getActuatorUrl(), "jvm.threads.daemon");
        Optional<Double> gcPause = actuatorClient.fetchMetricValue(service.getActuatorUrl(), "jvm.gc.pause", "statistic:TOTAL_TIME");
        Optional<Double> processCpu = actuatorClient.fetchMetricValue(service.getActuatorUrl(), "process.cpu.usage");

        MetricSnapshotRequest request = MetricSnapshotRequest.builder()
                .serviceId(service.getId())
                .endpoint("actuator/health")
                .responseTimeMs(responseTimeMs)
                .status(status)
                .cpuUsage(cpuUsage.map(v -> v * 100).orElse(null))
                .heapUsedMb(heapUsed.map(v -> (long) (v / 1_048_576)).orElse(null))
                .heapMaxMb(heapMax.map(v -> (long) (v / 1_048_576)).orElse(null))
                .nonHeapUsedMb(nonHeapUsed.map(v -> (long) (v / 1_048_576)).orElse(null))
                .threadsLive(threadsLive.map(v -> v.intValue()).orElse(null))
                .threadsDaemon(threadsDaemon.map(v -> v.intValue()).orElse(null))
                .gcPauseMs(gcPause.map(v -> v * 1000.0).orElse(null))
                .processCpuUsage(processCpu.map(v -> v * 100.0).orElse(null))
                .errorMessage(status == HealthStatus.DOWN ? "Service unavailable" : null)
                .build();

        metricsPersistenceService.saveEndpointSnapshot(request, MetricSource.PULL);

        service.setStatus(status);
        service.setLastSeenAt(LocalDateTime.now());
        serviceRepository.save(service);

        log.debug("Polled {}: status={}, responseTime={}ms", service.getName(), status, responseTimeMs);
    }
}
