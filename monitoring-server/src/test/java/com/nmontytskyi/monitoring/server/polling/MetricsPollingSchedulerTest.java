package com.nmontytskyi.monitoring.server.polling;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.dto.request.MetricSnapshotRequest;
import com.nmontytskyi.monitoring.server.dto.response.MetricRecordResponse;
import com.nmontytskyi.monitoring.server.entity.MetricRecordEntity.MetricSource;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import com.nmontytskyi.monitoring.server.repository.RegisteredServiceRepository;
import com.nmontytskyi.monitoring.server.service.MetricsPersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricsPollingSchedulerTest {

    @Mock
    private ActuatorClient actuatorClient;

    @Mock
    private RegisteredServiceRepository serviceRepository;

    @Mock
    private MetricsPersistenceService metricsPersistenceService;

    @InjectMocks
    private MetricsPollingScheduler scheduler;

    @Test
    void pollAllServices_callsPollForEachService() {
        List<RegisteredServiceEntity> services = List.of(
                buildService(1L, "http://localhost:8081/actuator"),
                buildService(2L, "http://localhost:8082/actuator"),
                buildService(3L, "http://localhost:8083/actuator")
        );
        when(serviceRepository.findAll()).thenReturn(services);
        when(actuatorClient.fetchHealth(anyString())).thenReturn(Optional.of(HealthStatus.UP));
        when(actuatorClient.fetchMetricValue(anyString(), anyString())).thenReturn(Optional.empty());
        when(metricsPersistenceService.saveEndpointSnapshot(any(), any()))
                .thenReturn(mock(MetricRecordResponse.class));

        scheduler.pollAllServices();

        verify(metricsPersistenceService, times(3)).saveEndpointSnapshot(any(), eq(MetricSource.PULL));
    }

    @Test
    void pollSingleService_healthUp_savesUpMetric() {
        RegisteredServiceEntity service = buildService(1L, "http://localhost:8081/actuator");
        when(serviceRepository.findAll()).thenReturn(List.of(service));
        when(actuatorClient.fetchHealth("http://localhost:8081/actuator"))
                .thenReturn(Optional.of(HealthStatus.UP));
        when(actuatorClient.fetchMetricValue(anyString(), anyString())).thenReturn(Optional.empty());
        when(metricsPersistenceService.saveEndpointSnapshot(any(), any()))
                .thenReturn(mock(MetricRecordResponse.class));

        scheduler.pollAllServices();

        ArgumentCaptor<MetricSnapshotRequest> requestCaptor = ArgumentCaptor.forClass(MetricSnapshotRequest.class);
        ArgumentCaptor<MetricSource> sourceCaptor = ArgumentCaptor.forClass(MetricSource.class);
        verify(metricsPersistenceService).saveEndpointSnapshot(requestCaptor.capture(), sourceCaptor.capture());

        assertThat(requestCaptor.getValue().getStatus()).isEqualTo(HealthStatus.UP);
        assertThat(sourceCaptor.getValue()).isEqualTo(MetricSource.PULL);
    }

    @Test
    void pollSingleService_serviceUnavailable_savesDownMetric() {
        RegisteredServiceEntity service = buildService(1L, "http://localhost:8081/actuator");
        when(serviceRepository.findAll()).thenReturn(List.of(service));
        when(actuatorClient.fetchHealth("http://localhost:8081/actuator"))
                .thenReturn(Optional.of(HealthStatus.DOWN));
        when(actuatorClient.fetchMetricValue(anyString(), anyString())).thenReturn(Optional.empty());
        when(metricsPersistenceService.saveEndpointSnapshot(any(), any()))
                .thenReturn(mock(MetricRecordResponse.class));

        scheduler.pollAllServices();

        ArgumentCaptor<MetricSnapshotRequest> requestCaptor = ArgumentCaptor.forClass(MetricSnapshotRequest.class);
        verify(metricsPersistenceService).saveEndpointSnapshot(requestCaptor.capture(), eq(MetricSource.PULL));
        assertThat(requestCaptor.getValue().getStatus()).isEqualTo(HealthStatus.DOWN);
        assertThat(requestCaptor.getValue().getErrorMessage()).isEqualTo("Service unavailable");
    }

    @Test
    void pollSingleService_emptyActuatorUrl_skipsPolling() {
        RegisteredServiceEntity service = buildService(1L, null);
        when(serviceRepository.findAll()).thenReturn(List.of(service));
        when(actuatorClient.fetchHealth(null)).thenReturn(Optional.empty());

        scheduler.pollAllServices();

        verify(metricsPersistenceService, never()).saveEndpointSnapshot(any(), any());
    }

    @Test
    void pollAllServices_updatesLastSeenAt() {
        RegisteredServiceEntity service = buildService(1L, "http://localhost:8081/actuator");
        when(serviceRepository.findAll()).thenReturn(List.of(service));
        when(actuatorClient.fetchHealth(anyString())).thenReturn(Optional.of(HealthStatus.UP));
        when(actuatorClient.fetchMetricValue(anyString(), anyString())).thenReturn(Optional.empty());
        when(metricsPersistenceService.saveEndpointSnapshot(any(), any()))
                .thenReturn(mock(MetricRecordResponse.class));

        scheduler.pollAllServices();

        assertThat(service.getLastSeenAt()).isNotNull();
        verify(serviceRepository).save(service);
    }

    private RegisteredServiceEntity buildService(Long id, String actuatorUrl) {
        return RegisteredServiceEntity.builder()
                .id(id)
                .name("service-" + id)
                .host("localhost")
                .port(8080 + id.intValue())
                .actuatorUrl(actuatorUrl)
                .status(HealthStatus.UNKNOWN)
                .build();
    }
}
