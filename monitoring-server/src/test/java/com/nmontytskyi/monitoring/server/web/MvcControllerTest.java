package com.nmontytskyi.monitoring.server.web;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.model.SlaDefinition;
import com.nmontytskyi.monitoring.model.SlaReport;
import com.nmontytskyi.monitoring.server.dto.response.AggregateMetricsResponse;
import com.nmontytskyi.monitoring.server.dto.response.AlertRuleResponse;
import com.nmontytskyi.monitoring.server.dto.response.DashboardSummaryResponse;
import com.nmontytskyi.monitoring.server.dto.response.ServiceResponse;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity;
import com.nmontytskyi.monitoring.server.exception.ServiceNotFoundException;
import com.nmontytskyi.monitoring.server.service.AlertEventService;
import com.nmontytskyi.monitoring.server.service.AlertRuleService;
import com.nmontytskyi.monitoring.server.service.MetricsPersistenceService;
import com.nmontytskyi.monitoring.server.service.RegisteredServiceService;
import com.nmontytskyi.monitoring.server.sla.SlaCalculationService;
import com.nmontytskyi.monitoring.server.sla.SlaWindow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(MvcController.class)
class MvcControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegisteredServiceService serviceService;

    @MockBean
    private MetricsPersistenceService metricsService;

    @MockBean
    private SlaCalculationService slaService;

    @MockBean
    private AlertRuleService alertRuleService;

    @MockBean
    private AlertEventService alertEventService;

    // ── 1 ──────────────────────────────────────────────────────────────────

    @Test
    void dashboard_returns200AndContainsServicesTable() throws Exception {
        when(serviceService.findAll()).thenReturn(List.of(buildService(1L, "order-svc", HealthStatus.UP)));
        when(metricsService.getAggregate(any(), any(), any())).thenReturn(emptyAggregate());
        when(metricsService.getLatest(any())).thenReturn(Optional.empty());
        when(alertEventService.findByServiceId(any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("summary"))
                .andExpect(content().string(containsString("<table")));
    }

    // ── 2 ──────────────────────────────────────────────────────────────────

    @Test
    void dashboard_emptyServices_showsZeroCounts() throws Exception {
        when(serviceService.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(content().string(containsString(">0<")));
    }

    // ── 3 ──────────────────────────────────────────────────────────────────

    @Test
    void serviceDetail_existingService_returns200() throws Exception {
        ServiceResponse svc = buildService(1L, "order-svc", HealthStatus.UP);
        when(serviceService.findById(1L)).thenReturn(svc);
        when(metricsService.getAggregate(eq(1L), any(), any())).thenReturn(emptyAggregate());
        when(slaService.calculate(eq(1L), eq(SlaWindow.DAY))).thenReturn(buildSlaReport());

        mockMvc.perform(get("/services/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("service-detail"))
                .andExpect(model().attributeExists("service", "aggregate", "sla"));
    }

    // ── 4 ──────────────────────────────────────────────────────────────────

    @Test
    void serviceDetail_unknownService_returns404() throws Exception {
        when(serviceService.findById(999L)).thenThrow(new ServiceNotFoundException(999L));

        mockMvc.perform(get("/services/999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("errors/404"));
    }

    // ── 5 ──────────────────────────────────────────────────────────────────

    @Test
    void slaReport_defaultWindowIsDay() throws Exception {
        ServiceResponse svc = buildService(1L, "order-svc", HealthStatus.UP);
        when(serviceService.findById(1L)).thenReturn(svc);
        when(slaService.calculate(eq(1L), eq(SlaWindow.DAY))).thenReturn(buildSlaReport());

        mockMvc.perform(get("/services/1/sla"))
                .andExpect(status().isOk());

        verify(slaService).calculate(1L, SlaWindow.DAY);
    }

    // ── 6 ──────────────────────────────────────────────────────────────────

    @Test
    void slaReport_weekWindow_callsServiceWithWeek() throws Exception {
        ServiceResponse svc = buildService(1L, "order-svc", HealthStatus.UP);
        when(serviceService.findById(1L)).thenReturn(svc);
        when(slaService.calculate(eq(1L), eq(SlaWindow.WEEK))).thenReturn(buildSlaReport());

        mockMvc.perform(get("/services/1/sla").param("window", "WEEK"))
                .andExpect(status().isOk());

        verify(slaService).calculate(1L, SlaWindow.WEEK);
    }

    // ── 7 ──────────────────────────────────────────────────────────────────

    @Test
    void alerts_returns200WithRulesAndEvents() throws Exception {
        when(serviceService.findAll()).thenReturn(List.of(buildService(1L, "order-svc", HealthStatus.UP)));
        when(alertRuleService.findByServiceId(null)).thenReturn(List.of(buildRuleResponse()));
        when(alertEventService.findByServiceId(any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/alerts"))
                .andExpect(status().isOk())
                .andExpect(view().name("alerts"))
                .andExpect(model().attributeExists("rules", "events", "services"));
    }

    // ── 8 ──────────────────────────────────────────────────────────────────

    @Test
    void createAlertRule_validRequest_redirectsToAlerts() throws Exception {
        when(alertRuleService.create(any())).thenReturn(buildRuleResponse());

        mockMvc.perform(post("/alerts/rules")
                        .param("serviceId", "1")
                        .param("metricType", "RESPONSE_TIME_AVG")
                        .param("comparator", "GT")
                        .param("threshold", "500")
                        .param("cooldownMinutes", "15")
                        .param("enabled", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/alerts"));

        verify(alertRuleService).create(any());
    }

    // ── 9 ──────────────────────────────────────────────────────────────────

    @Test
    void deleteAlertRule_existingId_redirectsToAlerts() throws Exception {
        doNothing().when(alertRuleService).deleteById(1L);

        mockMvc.perform(post("/alerts/rules/1/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/alerts"));

        verify(alertRuleService).deleteById(1L);
    }

    // ── 10 ─────────────────────────────────────────────────────────────────

    @Test
    void dashboard_statusBadgesRendered_forAllStatuses() throws Exception {
        List<ServiceResponse> services = List.of(
                buildService(1L, "down-svc",     HealthStatus.DOWN),
                buildService(2L, "degraded-svc", HealthStatus.DEGRADED)
        );
        when(serviceService.findAll()).thenReturn(services);
        when(metricsService.getAggregate(any(), any(), any())).thenReturn(emptyAggregate());
        when(metricsService.getLatest(any())).thenReturn(Optional.empty());
        when(alertEventService.findByServiceId(any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("DOWN")))
                .andExpect(content().string(containsString("DEGRADED")));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private ServiceResponse buildService(Long id, String name, HealthStatus status) {
        return ServiceResponse.builder()
                .id(id)
                .name(name)
                .host("localhost")
                .port(8080)
                .actuatorUrl("http://localhost:8080/actuator")
                .baseUrl("http://localhost:8080")
                .status(status)
                .registeredAt(LocalDateTime.now())
                .lastSeenAt(LocalDateTime.now())
                .build();
    }

    private AggregateMetricsResponse emptyAggregate() {
        return AggregateMetricsResponse.builder()
                .avgResponseTimeMs(0)
                .minResponseTimeMs(0)
                .maxResponseTimeMs(0)
                .totalRequests(0)
                .uptimePercent(0)
                .build();
    }

    private SlaReport buildSlaReport() {
        return SlaReport.builder()
                .serviceId("1")
                .from(LocalDateTime.now().minusDays(1))
                .to(LocalDateTime.now())
                .actualUptimePercent(99.9)
                .actualAvgResponseTimeMs(200.0)
                .actualErrorRatePercent(0.1)
                .p50ResponseTimeMs(180L)
                .p95ResponseTimeMs(350L)
                .p99ResponseTimeMs(800L)
                .sla(SlaDefinition.defaults())
                .uptimeMet(true)
                .responseTimeMet(true)
                .errorRateMet(true)
                .build();
    }

    private AlertRuleResponse buildRuleResponse() {
        return AlertRuleResponse.builder()
                .id(1L)
                .serviceId(1L)
                .metricType(AlertRuleEntity.MetricType.RESPONSE_TIME_AVG)
                .comparator(AlertRuleEntity.Comparator.GT)
                .threshold(500.0)
                .enabled(true)
                .cooldownMinutes(15)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private DashboardSummaryResponse buildSummary(List<DashboardSummaryResponse.ServiceSummary> summaries) {
        return DashboardSummaryResponse.builder()
                .services(summaries)
                .totalServices(summaries.size())
                .healthyCount(0L)
                .degradedCount(0L)
                .downCount(0L)
                .build();
    }
}
