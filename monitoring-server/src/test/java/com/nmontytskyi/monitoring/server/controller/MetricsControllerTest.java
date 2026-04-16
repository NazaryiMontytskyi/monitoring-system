package com.nmontytskyi.monitoring.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.dto.request.MetricSnapshotRequest;
import com.nmontytskyi.monitoring.server.dto.response.AggregateMetricsResponse;
import com.nmontytskyi.monitoring.server.dto.response.MetricRecordResponse;
import com.nmontytskyi.monitoring.server.service.MetricsPersistenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MetricsController.class)
class MetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MetricsPersistenceService service;

    @Test
    void post_pushSnapshot_returns202() throws Exception {
        MetricSnapshotRequest req = MetricSnapshotRequest.builder()
                .serviceId(1L)
                .endpoint("GET /items")
                .responseTimeMs(150L)
                .status(HealthStatus.UP)
                .build();

        MetricRecordResponse resp = MetricRecordResponse.builder()
                .id(1L)
                .serviceId(1L)
                .endpoint("GET /items")
                .responseTimeMs(150L)
                .status(HealthStatus.UP)
                .source("PUSH")
                .recordedAt(LocalDateTime.now())
                .build();

        when(service.saveEndpointSnapshot(any())).thenReturn(resp);

        mockMvc.perform(post("/api/metrics/endpoint")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.source").value("PUSH"));
    }

    @Test
    void get_latest_returns200() throws Exception {
        MetricRecordResponse resp = MetricRecordResponse.builder()
                .id(1L)
                .serviceId(1L)
                .endpoint("GET /items")
                .responseTimeMs(100L)
                .status(HealthStatus.UP)
                .source("PUSH")
                .recordedAt(LocalDateTime.now())
                .build();

        when(service.getLatest(1L)).thenReturn(Optional.of(resp));

        mockMvc.perform(get("/api/metrics/1/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(1));
    }

    @Test
    void get_aggregate_returns200() throws Exception {
        AggregateMetricsResponse resp = AggregateMetricsResponse.builder()
                .avgResponseTimeMs(200.0)
                .minResponseTimeMs(100.0)
                .maxResponseTimeMs(400.0)
                .totalRequests(50L)
                .uptimePercent(98.0)
                .build();

        when(service.getAggregate(eq(1L), any(), any())).thenReturn(resp);

        mockMvc.perform(get("/api/metrics/1/aggregate")
                        .param("from", "2025-01-01T00:00:00")
                        .param("to", "2025-01-02T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avgResponseTimeMs").value(200.0))
                .andExpect(jsonPath("$.uptimePercent").value(98.0));
    }
}
