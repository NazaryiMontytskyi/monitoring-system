package com.nmontytskyi.monitoring.server.controller;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.dto.response.AggregateMetricsResponse;
import com.nmontytskyi.monitoring.server.dto.response.AlertEventResponse;
import com.nmontytskyi.monitoring.server.dto.response.MetricRecordResponse;
import com.nmontytskyi.monitoring.server.dto.response.ServiceResponse;
import com.nmontytskyi.monitoring.server.service.AlertEventService;
import com.nmontytskyi.monitoring.server.service.MetricsPersistenceService;
import com.nmontytskyi.monitoring.server.service.RegisteredServiceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardApiController.class)
class DashboardApiControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private RegisteredServiceService registeredServiceService;
    @MockBean private MetricsPersistenceService metricsPersistenceService;
    @MockBean private AlertEventService alertEventService;

    private ServiceResponse buildService(Long id, HealthStatus status) {
        return ServiceResponse.builder()
                .id(id)
                .name("svc-" + id)
                .host("localhost")
                .port(8080 + id.intValue())
                .status(status)
                .build();
    }

    // ── GET /api/dashboard ───────────────────────────────────────────────────

    @Test
    void getDashboard_noServices_returnsEmptySummary() throws Exception {
        when(registeredServiceService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalServices").value(0))
                .andExpect(jsonPath("$.healthyCount").value(0))
                .andExpect(jsonPath("$.degradedCount").value(0))
                .andExpect(jsonPath("$.downCount").value(0))
                .andExpect(jsonPath("$.services").isArray())
                .andExpect(jsonPath("$.services").isEmpty());
    }

    @Test
    void getDashboard_mixedStatuses_correctCounts() throws Exception {
        ServiceResponse up = buildService(1L, HealthStatus.UP);
        ServiceResponse down = buildService(2L, HealthStatus.DOWN);
        ServiceResponse degraded = buildService(3L, HealthStatus.DEGRADED);

        when(registeredServiceService.findAll()).thenReturn(List.of(up, down, degraded));
        when(metricsPersistenceService.getAggregate(anyLong(), any(), any()))
                .thenReturn(AggregateMetricsResponse.builder()
                        .avgResponseTimeMs(100)
                        .totalRequests(0)
                        .build());
        when(metricsPersistenceService.getLatest(anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalServices").value(3))
                .andExpect(jsonPath("$.healthyCount").value(1))
                .andExpect(jsonPath("$.downCount").value(1))
                .andExpect(jsonPath("$.degradedCount").value(1))
                .andExpect(jsonPath("$.services").isArray());
    }

    @Test
    void getDashboard_serviceWithMetrics_includesAvgResponseTime() throws Exception {
        ServiceResponse svc = buildService(1L, HealthStatus.UP);
        when(registeredServiceService.findAll()).thenReturn(List.of(svc));
        when(metricsPersistenceService.getAggregate(eq(1L), any(), any()))
                .thenReturn(AggregateMetricsResponse.builder()
                        .avgResponseTimeMs(250)
                        .uptimePercent(99.5)
                        .totalRequests(100)
                        .build());
        when(metricsPersistenceService.getLatest(eq(1L)))
                .thenReturn(Optional.of(MetricRecordResponse.builder().cpuUsage(0.45).build()));

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[0].name").value("svc-1"))
                .andExpect(jsonPath("$.services[0].avgResponseTimeMs").value(250.0))
                .andExpect(jsonPath("$.services[0].uptimePercent").value(99.5))
                .andExpect(jsonPath("$.services[0].cpuUsage").value(0.45));
    }

    @Test
    void getDashboard_metricsServiceThrows_stillReturnsServiceInList() throws Exception {
        ServiceResponse svc = buildService(1L, HealthStatus.UP);
        when(registeredServiceService.findAll()).thenReturn(List.of(svc));
        when(metricsPersistenceService.getAggregate(anyLong(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));
        when(metricsPersistenceService.getLatest(anyLong()))
                .thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[0].name").value("svc-1"))
                .andExpect(jsonPath("$.services[0].avgResponseTimeMs").doesNotExist());
    }

    @Test
    void getDashboard_zeroRequests_avgResponseTimeIsNull() throws Exception {
        ServiceResponse svc = buildService(1L, HealthStatus.UP);
        when(registeredServiceService.findAll()).thenReturn(List.of(svc));
        when(metricsPersistenceService.getAggregate(eq(1L), any(), any()))
                .thenReturn(AggregateMetricsResponse.builder()
                        .totalRequests(0)
                        .avgResponseTimeMs(0)
                        .build());
        when(metricsPersistenceService.getLatest(anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[0].avgResponseTimeMs").doesNotExist());
    }

    // ── GET /api/dashboard/recent-events ─────────────────────────────────────

    @Test
    void getRecentEvents_noServices_returnsEmptyList() throws Exception {
        when(registeredServiceService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/dashboard/recent-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getRecentEvents_returnsAtMostFiveNewestEvents() throws Exception {
        ServiceResponse svc = buildService(1L, HealthStatus.UP);
        when(registeredServiceService.findAll()).thenReturn(List.of(svc));

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 12, 0);
        List<AlertEventResponse> events = List.of(
                buildEvent(1L, base.plusMinutes(1)),
                buildEvent(2L, base.plusMinutes(2)),
                buildEvent(3L, base.plusMinutes(3)),
                buildEvent(4L, base.plusMinutes(4)),
                buildEvent(5L, base.plusMinutes(5)),
                buildEvent(6L, base.plusMinutes(6))
        );

        when(alertEventService.findByServiceId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(events));

        mockMvc.perform(get("/api/dashboard/recent-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].id").value(6));
    }

    @Test
    void getRecentEvents_alertServiceThrows_returnsEmptyList() throws Exception {
        ServiceResponse svc = buildService(1L, HealthStatus.UP);
        when(registeredServiceService.findAll()).thenReturn(List.of(svc));
        when(alertEventService.findByServiceId(anyLong(), any(Pageable.class)))
                .thenThrow(new RuntimeException("error"));

        mockMvc.perform(get("/api/dashboard/recent-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    private AlertEventResponse buildEvent(Long id, LocalDateTime firedAt) {
        return AlertEventResponse.builder()
                .id(id)
                .serviceId(1L)
                .firedAt(firedAt)
                .metricValue(100.0)
                .message("alert " + id)
                .build();
    }
}
