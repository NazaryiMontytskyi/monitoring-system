package com.nmontytskyi.monitoring.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nmontytskyi.monitoring.server.dto.request.AlertRuleRequest;
import com.nmontytskyi.monitoring.server.dto.response.AlertRuleResponse;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity;
import com.nmontytskyi.monitoring.server.service.AlertEventService;
import com.nmontytskyi.monitoring.server.service.AlertRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertController.class)
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AlertRuleService alertRuleService;

    @MockBean
    private AlertEventService alertEventService;

    @Test
    void post_createRule_returns201() throws Exception {
        AlertRuleRequest req = AlertRuleRequest.builder()
                .serviceId(1L)
                .metricType(AlertRuleEntity.MetricType.RESPONSE_TIME_AVG)
                .comparator(AlertRuleEntity.Comparator.GT)
                .threshold(1000.0)
                .enabled(true)
                .cooldownMinutes(15)
                .build();

        when(alertRuleService.create(any())).thenReturn(buildRuleResponse(10L));

        mockMvc.perform(post("/api/alerts/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void get_rules_returns200() throws Exception {
        when(alertRuleService.findByServiceId(1L)).thenReturn(List.of(buildRuleResponse(10L)));

        mockMvc.perform(get("/api/alerts/rules").param("serviceId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void delete_rule_returns204() throws Exception {
        doNothing().when(alertRuleService).deleteById(10L);

        mockMvc.perform(delete("/api/alerts/rules/10"))
                .andExpect(status().isNoContent());
    }

    private AlertRuleResponse buildRuleResponse(Long id) {
        return AlertRuleResponse.builder()
                .id(id)
                .serviceId(1L)
                .metricType(AlertRuleEntity.MetricType.RESPONSE_TIME_AVG)
                .comparator(AlertRuleEntity.Comparator.GT)
                .threshold(1000.0)
                .enabled(true)
                .cooldownMinutes(15)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
